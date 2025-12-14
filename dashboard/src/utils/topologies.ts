export interface TopologyNode {
    name: string,
    symbolSize: number
}

export interface TopologyLink {
    source: string,
    target: string
}

export interface Topology {
    nodes: TopologyNode[],
    links: TopologyLink[]
}

export function toTopology(topology: Record<string, string[]>): Topology {
    const nodes: TopologyNode[] = [];
    const links: TopologyLink[] = [];

    Object.keys(topology).forEach(nodeName => {
        putIfAbsent(nodes, nodeName);
        topology[nodeName].forEach(targetName => {
            putIfAbsent(nodes, targetName);
            links.push({source: nodeName, target: targetName});
            let targetNode = getNodeName(nodes, targetName);
            if (targetNode) {
                targetNode.symbolSize = targetNode.symbolSize + 2;
            }
        })
    })

    return {
        nodes,
        links
    }

}


function putIfAbsent(nodes: TopologyNode[], nodeName: string) {
    if (nodes.filter(_nodeName => {
        return _nodeName.name === nodeName;
    }).length === 0) {
        nodes.push({name: nodeName, symbolSize: 1})
    }
}


function getNodeName(nodes: TopologyNode[], nodeName: string) {
    return nodes.find(_nodeName => {
        return _nodeName.name === nodeName;
    });
}