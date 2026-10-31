---
layout: home

hero:
  name: CoSky
  text: Configuration and Service Discovery
  tagline: A small server in front of the Redis you already run, with clients that read from memory
  image:
    src: /cosky-logo.svg
    alt: CoSky
  actions:
    - theme: brand
      text: Get Started
      link: /guide/getting-started
    - theme: alt
      text: Overview
      link: /guide/
    - theme: alt
      text: View on GitHub
      link: https://github.com/Ahoo-Wang/CoSky

features:
  - title: Service Discovery
    icon: 🔍
    details: Applications register under a lease and disappear when it ends. Consumers see changes as they happen, through Spring Cloud or a Kotlin client.
  - title: Configuration Management
    icon: ⚙️
    details: Spring properties served from CoSky and refreshed live, with revisions, rollback and zip import and export.
  - title: Reads from Memory
    icon: 🚀
    details: Each client keeps a local view that a Watch stream keeps current, so reading a Config or choosing an instance costs no network round trip.
  - title: Keeps Working Through Failures
    icon: 🛡️
    details: Clients serve what they know when the server is away, and restarts resume from a snapshot on disk. A mass expiry after a partition is held back rather than obeyed.
  - title: Access Control on Every Call
    icon: 🔐
    details: ServiceAccount tokens and OIDC, with roles that say who may do what, down to a name prefix. Applications authenticate too, and never touch Redis.
  - title: Light to Run
    icon: 📦
    details: One image holds the server and the Dashboard. It needs Redis 7 and nothing else, and runs on Docker or Kubernetes.
---
