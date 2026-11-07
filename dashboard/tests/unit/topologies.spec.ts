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

import {expect, test} from '@playwright/test';
import type {Edge} from '@xyflow/react';
import {
    getConnectedNodeIds,
    isServiceNodeData,
    NODE_WIDTH,
    toConsumers,
    toReactFlowTopology,
} from '../../src/components/topology/topologies.ts';

test('recognizes only complete service node data', () => {
    expect(isServiceNodeData({label: 'gateway', nodeType: 'source', inDegree: 0, outDegree: 2})).toBe(true);
    expect(isServiceNodeData({label: 'gateway', nodeType: 'unknown', inDegree: 0, outDegree: 2})).toBe(false);
    expect(isServiceNodeData({label: 'gateway', nodeType: 'source', inDegree: '0', outDegree: 2})).toBe(false);
    expect(isServiceNodeData({label: 'gateway', nodeType: 'source', inDegree: Number.NaN, outDegree: 2})).toBe(false);
    expect(isServiceNodeData(null)).toBe(false);
});

test('returns only the selected node and its direct neighbors', () => {
    const edges: Edge[] = [
        {id: 'gateway-user', source: 'gateway', target: 'user'},
        {id: 'gateway-order', source: 'gateway', target: 'order'},
        {id: 'user-database', source: 'user', target: 'database'},
    ];

    expect([...getConnectedNodeIds('gateway', edges)].sort()).toEqual(['gateway', 'order', 'user']);
    expect([...getConnectedNodeIds('database', edges)].sort()).toEqual(['database', 'user']);
});

test('converts service relationships into deterministic typed nodes and quiet edges', () => {
    const {nodes, edges} = toReactFlowTopology({
        gateway: ['user', 'order'],
        user: ['database'],
        order: ['database'],
    });
    const node = (id: string) => nodes.find(item => item.id === id)!;

    expect(nodes).toHaveLength(4);
    expect(node('gateway')).toMatchObject({type: 'service', ariaLabel: 'gateway'});
    expect(node('gateway').data).toMatchObject({nodeType: 'source', inDegree: 0, outDegree: 2});
    expect(node('user').data).toMatchObject({nodeType: 'intermediate', inDegree: 1, outDegree: 1});
    expect(node('database').data).toMatchObject({nodeType: 'target', inDegree: 2, outDegree: 0});
    expect(new Set(nodes.map(item => `${item.position.x}:${item.position.y}`)).size).toBe(nodes.length);
    expect(edges.map(edge => edge.id)).toEqual([
        'gateway->user',
        'gateway->order',
        'user->database',
        'order->database',
    ]);
    expect(edges[0].style).toMatchObject({stroke: '#8b7aff', strokeWidth: 1, opacity: 0.28});
    expect(edges[0].animated).toBeUndefined();
});

test('returns an empty graph for an empty topology', () => {
    expect(toReactFlowTopology({})).toEqual({nodes: [], edges: []});
});

test('classifies cyclic services and preserves both directions', () => {
    const {nodes, edges} = toReactFlowTopology({
        billing: ['ledger'],
        ledger: ['billing'],
    });

    expect(nodes.map(node => node.data)).toEqual([
        {label: 'billing', nodeType: 'intermediate', inDegree: 1, outDegree: 1},
        {label: 'ledger', nodeType: 'intermediate', inDegree: 1, outDegree: 1},
    ]);
    expect(edges.map(edge => edge.id)).toEqual(['billing->ledger', 'ledger->billing']);
});

test('keeps apart relationships whose names differ only in where a hyphen falls', () => {
    const relationships = [
        {consumer: 'a-b', producer: 'c'},
        {consumer: 'a', producer: 'b-c'},
        {consumer: 'c', producer: 'a-b'},
        {consumer: 'b-c', producer: 'a'},
    ];
    const {edges} = toReactFlowTopology(toConsumers(relationships));

    expect(new Set(edges.map(edge => edge.id)).size).toBe(relationships.length);
    expect(edges.map(({source, target}) => ({consumer: source, producer: target}))).toEqual(relationships);
});

test('wraps crowded layers without overlapping nodes', () => {
    const topology = Object.fromEntries(
        Array.from({length: 7}, (_, index) => [`service-${index}`, []]),
    );
    const {nodes} = toReactFlowTopology(topology);
    const position = (id: string) => nodes.find(node => node.id === id)!.position;

    expect(position('service-0').y).toBe(220);
    expect(position('service-6').y).toBe(340);
    expect(new Set(nodes.map(node => `${node.position.x}:${node.position.y}`)).size).toBe(7);
});

test('places each layer below every row of the crowded layer above it', () => {
    // About the box of a ServiceNode whose name takes all its lines: nodes placed closer than this overlap on screen.
    const box = {width: NODE_WIDTH, height: 93};
    const named = (prefix: string, count: number) =>
        Array.from({length: count}, (_, index) => `${prefix}-${String(index).padStart(2, '0')}`);
    // Three rows of sources, then three rows of the services they call, then the database those call.
    const sources = named('source', 13);
    const middles = named('middle', 13);
    const {nodes} = toReactFlowTopology({
        ...Object.fromEntries(sources.map((source, index) => [source, [middles[index]]])),
        ...Object.fromEntries(middles.map(middle => [middle, ['database']])),
    });
    const y = (id: string) => nodes.find(node => node.id === id)!.position.y;
    const overlap = (a: {x: number; y: number}, b: {x: number; y: number}) =>
        Math.abs(a.x - b.x) < box.width && Math.abs(a.y - b.y) < box.height;

    for (const [index, node] of nodes.entries()) {
        for (const other of nodes.slice(index + 1)) {
            expect(overlap(node.position, other.position), `${node.id} overlaps ${other.id}`).toBe(false);
        }
    }
    expect(Math.min(...middles.map(y))).toBeGreaterThanOrEqual(Math.max(...sources.map(y)) + box.height);
    expect(y('database')).toBeGreaterThanOrEqual(Math.max(...middles.map(y)) + box.height);
});
