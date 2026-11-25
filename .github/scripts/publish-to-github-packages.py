#!/usr/bin/env python3
#
# Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#      http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Publishes a release that Gradle staged in each module's build/repos to the Maven registry of GitHub Packages.

    ./gradlew publishAllPublicationsToProjectBuildRepoRepository
    GITHUB_TOKEN=... python3 .github/scripts/publish-to-github-packages.py \\
        https://maven.pkg.github.com/OWNER/REPO me.ahoo.cosky 8.0.0

GitHub Packages never replaces a file: uploading one that is already there answers 409 (Conflict). Gradle publishes a
module as a whole and stops at the first such answer, so a publish that failed halfway could never be finished by
running it again, nor one that met a 409 for a file GitHub had in fact accepted, from an upload Gradle sent again after
a timeout. This uploads one file at a time and takes a 409 for exactly that case, the file being there with the same
bytes. Anything else fails it, a different file under the same name included. A re-run so uploads what is missing and
changes nothing that is published, and the version it leaves is the one a single successful run would have.

- A checksum is computed from the file that ends up published rather than copied from the staging directory.
- A signature that is already there is kept although a new one differs (a signature carries its time): it signs the
  same bytes.
- In each module the Gradle module metadata and then the POM go last, so the version resolves only once the files they
  point to are there. maven-metadata.xml, the list of versions, follows them, merged the way Gradle merges it.
"""

import argparse
import base64
import datetime
import hashlib
import os
import sys
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ElementTree
from pathlib import Path

CHECKSUMS = ("md5", "sha1", "sha256", "sha512")
SIGNATURE = "asc"
METADATA = "maven-metadata.xml"
# Each request: a timeout, a dropped connection, a 429 or a 5xx is tried again, up to this many times in all.
ATTEMPTS = 6
TRANSIENT_STATUSES = {408, 429, 500, 502, 503, 504}
# A 409 for a file that is not there, which GitHub Packages has been known to answer, is tried again as well.
CONFLICT_ATTEMPTS = 3
SKIPPED_DIRECTORIES = {"build", "src", "node_modules", ".git", ".gradle"}


class PublishError(Exception):
    pass


def excerpt(body):
    return body[:300].decode("utf-8", "replace").strip()


def retry_delay(attempt, retry_after):
    if retry_after and retry_after.strip().isdigit():
        return min(int(retry_after), 300)
    return min(2 ** (attempt + 1), 60)


class Registry:
    def __init__(self, url, user, token):
        self.url = url.rstrip("/")
        self.authorization = "Basic " + base64.b64encode(f"{user}:{token}".encode()).decode()

    def request(self, method, url, data=None):
        """Returns the status and body of the response, after trying a transient failure again."""
        for attempt in range(1, ATTEMPTS + 1):
            request = urllib.request.Request(url, data=data, method=method)
            # Not passed on when a download redirects to storage, whose URL carries a signature of its own.
            request.add_unredirected_header("Authorization", self.authorization)
            if data is not None:
                request.add_header("Content-Type", "application/octet-stream")
            try:
                with urllib.request.urlopen(request, timeout=300) as response:
                    return response.status, response.read()
            except urllib.error.HTTPError as error:
                status, body = error.code, error.read()
                if status not in TRANSIENT_STATUSES or attempt == ATTEMPTS:
                    return status, body
                problem, retry_after = f"HTTP {status}", error.headers.get("Retry-After")
            except OSError as error:  # URLError, a timeout, a dropped connection
                if attempt == ATTEMPTS:
                    raise PublishError(f"{method} {url}: {error}") from error
                problem, retry_after = str(error) or type(error).__name__, None
            delay = retry_delay(attempt, retry_after)
            print(f"  {method} {url}: {problem}, trying again in {delay}s", flush=True)
            time.sleep(delay)
        raise AssertionError("unreachable")

    def publish(self, url, content, keep_different=False):
        """Uploads content unless the file is there already. Returns the bytes now published and whether it sent them."""
        for attempt in range(1, CONFLICT_ATTEMPTS + 1):
            status, body = self.request("PUT", url, content)
            if 200 <= status < 300:
                return content, True
            if status != 409:
                raise PublishError(f"PUT {url}: HTTP {status} {excerpt(body)}")
            status, published = self.request("GET", url)
            if status == 200:
                if published != content and not keep_different:
                    raise PublishError(
                        f"{url} is already published with different content: an earlier run published another build "
                        "of this version. Delete the version on GitHub Packages and run the job again."
                    )
                return published, False
            if status != 404:
                raise PublishError(f"GET {url}: HTTP {status} {excerpt(published)}")
            if attempt < CONFLICT_ATTEMPTS:
                print(f"  PUT {url}: HTTP 409, yet the file is not there, trying again", flush=True)
                time.sleep(retry_delay(attempt, None))
        raise PublishError(f"PUT {url}: HTTP 409, yet the file is not there: {excerpt(body)}")


def is_companion(name):
    return name.rsplit(".", 1)[-1] in (*CHECKSUMS, SIGNATURE)


def upload_rank(name):
    return name.endswith(".module") or name.endswith(".pom"), name.endswith(".pom"), name


def checksum(algorithm, content):
    return hashlib.new(algorithm, content).hexdigest().encode()


def staged_artifacts(root, group, version):
    """The version directory of each artifact staged under a build/repos below root, by artifact id."""
    staged = {}
    for directory, children, _ in os.walk(root):
        if "build" in children:
            group_directory = Path(directory, "build", "repos", *group.split("."))
            for version_directory in sorted(group_directory.glob(f"*/{version}")):
                artifact_id = version_directory.parent.name
                if artifact_id in staged:
                    raise PublishError(f"{artifact_id} is staged twice: in {staged[artifact_id]} and {version_directory}")
                staged[artifact_id] = version_directory
        children[:] = sorted(child for child in children if child not in SKIPPED_DIRECTORIES)
    return staged


class Artifact:
    def __init__(self, registry, group, artifact_id, version_directory):
        self.registry = registry
        self.version = version_directory.name
        self.version_directory = version_directory
        self.metadata_directory = version_directory.parent
        self.url = f"{registry.url}/{group.replace('.', '/')}/{artifact_id}"
        self.uploaded = 0
        self.present = 0

    def publish_file(self, name, content, keep_different=False):
        published, sent = self.registry.publish(f"{self.url}/{self.version}/{name}", content, keep_different)
        if sent:
            self.uploaded += 1
        else:
            self.present += 1
        return published, sent

    def publish_checksums(self, staged, name, content):
        for algorithm in CHECKSUMS:
            if f"{name}.{algorithm}" in staged:
                self.publish_file(f"{name}.{algorithm}", checksum(algorithm, content))
                staged.discard(f"{name}.{algorithm}")

    def publish_version(self):
        staged = {path.name for path in self.version_directory.iterdir() if path.is_file()}
        for name in sorted((name for name in staged if not is_companion(name)), key=upload_rank):
            published, sent = self.publish_file(name, (self.version_directory / name).read_bytes())
            staged.discard(name)
            self.publish_checksums(staged, name, published)
            signature = f"{name}.{SIGNATURE}"
            if signature in staged:
                # A signature already there signs the same bytes only if the file it signs was there as well.
                published_signature, _ = self.publish_file(
                    signature, (self.version_directory / signature).read_bytes(), keep_different=not sent
                )
                staged.discard(signature)
                self.publish_checksums(staged, signature, published_signature)
        if staged:
            raise PublishError(
                f"{self.version_directory} holds files that are neither published files nor their checksums or "
                f"signatures: {', '.join(sorted(staged))}"
            )

    def publish_metadata(self):
        """Adds the version to the list of versions, as Gradle does; leaves a list that has it already alone."""
        url = f"{self.url}/{METADATA}"
        status, body = self.registry.request("GET", url)
        if status == 200:
            try:
                content = merge_metadata(body, self.version)
            except ElementTree.ParseError as error:
                raise PublishError(f"GET {url}: not a {METADATA} ({error}): {excerpt(body)}") from error
            if content is None:
                return f"{METADATA} lists {self.version} already"
        elif status == 404:
            content = (self.metadata_directory / METADATA).read_bytes()
        else:
            raise PublishError(f"GET {url}: HTTP {status} {excerpt(body)}")
        for name, data in [(METADATA, content)] + [
            (f"{METADATA}.{algorithm}", checksum(algorithm, content))
            for algorithm in CHECKSUMS
            if (self.metadata_directory / f"{METADATA}.{algorithm}").is_file()
        ]:
            status, body = self.registry.request("PUT", f"{self.url}/{name}", data)
            if not 200 <= status < 300:
                raise PublishError(f"PUT {self.url}/{name}: HTTP {status} {excerpt(body)}")
        return f"{METADATA} updated"


def merge_metadata(document, version):
    """The document with version added to its versions, or None if it lists the version already."""
    root = ElementTree.fromstring(document)
    namespace = root.tag[: root.tag.index("}") + 1] if root.tag.startswith("{") else ""
    if namespace:
        ElementTree.register_namespace("", namespace[1:-1])

    def child(parent, tag):
        found = parent.find(namespace + tag)
        return found if found is not None else ElementTree.SubElement(parent, namespace + tag)

    versioning = child(root, "versioning")
    versions = child(versioning, "versions")
    if any(element.text == version for element in versions.iter(namespace + "version")):
        return None
    ElementTree.SubElement(versions, namespace + "version").text = version
    child(versioning, "latest").text = version
    child(versioning, "release").text = version
    now = datetime.datetime.now(datetime.timezone.utc)
    child(versioning, "lastUpdated").text = now.strftime("%Y%m%d%H%M%S")
    return ElementTree.tostring(root, encoding="UTF-8", xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("repository", help="the Maven repository, https://maven.pkg.github.com/OWNER/REPO")
    parser.add_argument("group", help="the group of the artifacts, me.ahoo.cosky")
    parser.add_argument("version", help="the version to publish")
    parser.add_argument("--root", default=".", help="where to look for the build/repos directories")
    args = parser.parse_args()
    if not args.group or not args.version:
        parser.error("the group and the version must not be empty")
    if args.version.endswith("-SNAPSHOT"):
        parser.error("this publishes releases: a snapshot has metadata of its own")
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        parser.error("GITHUB_TOKEN is not set")
    registry = Registry(args.repository, os.environ.get("GITHUB_ACTOR") or "github-actions", token)

    staged = staged_artifacts(args.root, args.group, args.version)
    if not staged:
        print(
            f"::error::Nothing is staged for {args.group}:{args.version}. "
            "Run ./gradlew publishAllPublicationsToProjectBuildRepoRepository first."
        )
        return 1
    failed = []
    for artifact_id, version_directory in sorted(staged.items()):
        artifact = Artifact(registry, args.group, artifact_id, version_directory)
        try:
            artifact.publish_version()
            outcome = artifact.publish_metadata()
            print(f"{artifact_id}: {artifact.uploaded} files uploaded, {artifact.present} there already, {outcome}")
        except PublishError as error:
            print(f"{artifact_id}: {artifact.uploaded} files uploaded, {artifact.present} there already, then failed")
            print(f"::error::{error}")
            failed.append(artifact_id)
        sys.stdout.flush()
    if failed:
        print(f"::error::{len(failed)} of {len(staged)} artifacts are not completely published: {', '.join(failed)}. "
              "Running the job again uploads only what is missing.")
        return 1
    print(f"All {len(staged)} artifacts of {args.group}:{args.version} are published.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
