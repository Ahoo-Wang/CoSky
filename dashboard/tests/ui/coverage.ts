/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {expect, test as base} from '@playwright/test';
import {CoverageReport} from 'monocart-coverage-reports';
import type {CoverageReportOptions} from 'monocart-coverage-reports';
import {readFileSync} from 'node:fs';
import path from 'node:path';
import ts from 'typescript';

export const coverageEnabled = process.env.COSKY_COVERAGE === '1';

type CoverageMetric = 'statements' | 'branches' | 'functions' | 'lines';

const coverageDirectory = path.resolve('coverage');
const thresholds: Record<CoverageMetric, number> = {
    statements: 80,
    branches: 75,
    functions: 80,
    lines: 80,
};

export const coverageOptions: CoverageReportOptions = {
    name: 'CoSky Dashboard Coverage',
    outputDir: coverageDirectory,
    reports: [
        ['v8', {metrics: ['statements', 'branches', 'functions', 'lines']}],
        'text-summary',
        ['json-summary', {file: 'coverage-summary.json'}],
        ['lcovonly', {file: 'lcov.info'}],
    ],
    logging: 'error',
    entryFilter: entry => {
        const pathname = new URL(entry.url).pathname;
        return pathname.startsWith('/src/') && !pathname.startsWith('/src/generated/') && /\.tsx?$/.test(pathname);
    },
    sourceFilter: sourcePath => !sourcePath.includes('node_modules') &&
        !sourcePath.includes('/generated/') && /\.tsx?$/.test(sourcePath),
    sourcePath: (filePath, info) => {
        const distFile = info.distFile?.replaceAll('\\', '/').split('?')[0];
        const distSourceIndex = distFile?.lastIndexOf('/src/') ?? -1;
        if (distFile && distSourceIndex >= 0) return path.resolve(distFile.slice(distSourceIndex + 1));
        const normalized = filePath.replaceAll('\\', '/');
        const sourceIndex = normalized.lastIndexOf('/src/');
        if (sourceIndex >= 0) return path.resolve(normalized.slice(sourceIndex + 1));
        return path.resolve(normalized);
    },
    all: {
        dir: './src',
        filter: filePath => {
            const normalized = filePath.replaceAll('\\', '/');
            if (normalized.includes('/generated/') || normalized.endsWith('.d.ts')) return false;
            return /\.tsx?$/.test(normalized) ? 'js' : false;
        },
        transformer: async entry => {
            const source = entry.source;
            const fileName = entry.url;
            const transpiled = ts.transpileModule(source, {
                fileName,
                compilerOptions: {
                    target: ts.ScriptTarget.ES2022,
                    module: ts.ModuleKind.ESNext,
                    jsx: ts.JsxEmit.ReactJSX,
                    sourceMap: true,
                },
            });
            entry.source = transpiled.outputText;
            entry.sourceMap = JSON.parse(transpiled.sourceMapText!);
            entry.sourceMap.sources = [fileName];
            entry.sourceMap.sourcesContent = [source];
        },
    },
    onEnd: () => {
        if (process.env.COSKY_COVERAGE_THRESHOLDS === 'off') return;
        const report = JSON.parse(
            readFileSync(path.join(coverageDirectory, 'coverage-summary.json'), 'utf8'),
        ) as {total: Record<CoverageMetric, {pct: number}>};
        const failures = Object.entries(thresholds).flatMap(([metric, minimum]) => {
            const actual = report.total[metric as CoverageMetric].pct;
            return actual < minimum
                ? [`${metric}: ${actual}% < ${minimum}%`]
                : [];
        });
        if (failures.length) throw new Error(`Dashboard coverage threshold failed:\n${failures.join('\n')}`);
    },
};

type CoverageFixture = {
    collectCoverage: void;
};

export const test = base.extend<CoverageFixture>({
    collectCoverage: [async ({page}, use) => {
        if (!coverageEnabled) {
            await use();
            return;
        }
        await page.coverage.startJSCoverage({resetOnNavigation: false});
        await use();
        const coverage = await page.coverage.stopJSCoverage();
        await new CoverageReport(coverageOptions).add(coverage);
    }, {auto: true}],
});

export {expect};
