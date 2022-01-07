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

import {Component, OnInit} from '@angular/core';
import {StatClient} from "../../api/stat/StatClient";
import {NamespaceContext} from "../../core/NamespaceContext";

@Component({
  selector: 'app-topology',
  templateUrl: './topology.component.html',
  styleUrls: ['./topology.component.scss']
})
export class TopologyComponent implements OnInit {
  topologyChart!: any;

  constructor(private namespaceContext: NamespaceContext, private statClient: StatClient) {
  }

  ngOnInit(): void {
    this.loadTopology();
    this.namespaceContext.subscribeNamespaceChanged('/topology', ns => {
      this.loadTopology();
    });
  }

  private loadTopology() {
    if (this.topologyChart) {
      this.topologyChart.dispose();
    }
    // @ts-ignore
    this.topologyChart = echarts.init(document.getElementById('topology'));
    this.topologyChart.showLoading();
    this.statClient.getTopology(this.namespaceContext.ensureCurrentNamespace()).subscribe(stat => {
      this.topologyChart.hideLoading();
      this.drawTopology(stat);
      this.topologyChart.resize();
    });
    window.onresize = () => {
      if (this.topologyChart) {
        this.topologyChart.resize();
      }
    }
  }

  private drawTopology(stat: Map<string, string[]>) {
    let chartNodes: ChartNode[] = [];
    let chartEdges: { source: string; target: string; }[] = [];
    Object.keys(stat).forEach(nodeName => {
      this.putIfAbsent(chartNodes, nodeName);
      // @ts-ignore
      stat[nodeName].forEach(targetName => {
        this.putIfAbsent(chartNodes, targetName);
        chartEdges.push({source: nodeName, target: targetName});
        let targetNode = this.ofNodeName(chartNodes, targetName);
        if (targetNode) {
          targetNode.symbolSize = targetNode.symbolSize + 2;
        }
      })
    })

    let topologyChartOption = {
      title: {
        text: 'Service Topology',
        show: false
      },
      animationDurationUpdate: 1500,
      animationEasingUpdate: 'quinticInOut',
      series: [
        {
          name: 'Service Topology',
          type: 'graph',
          layout: 'circular',
          circular: {
            rotateLabel: true
          },
          animation: false,
          label: {
            position: 'right',
            formatter: '{b}',
            show: true
          },
          lineStyle: {
            color: 'source',
            curveness: 0.2
          },
          draggable: true,
          edgeSymbol: ['circle', 'arrow'],
          // edgeSymbolSize: [20, 10],
          emphasis: {
            focus: 'adjacency',
            blurScope: 'coordinateSystem'
          },
          roam: true,
          data: chartNodes,
          edges: chartEdges
        }
      ]
    };
    this.topologyChart.setOption(topologyChartOption, true);
  }

  private ofNodeName(chartNodes: ChartNode[], nodeName: string): ChartNode | undefined {
    return chartNodes.find(_nodeName => {
      return _nodeName.name === nodeName;
    });
  }

  private putIfAbsent(chartNodes: ChartNode[], nodeName: string) {
    if (chartNodes.filter(_nodeName => {
      return _nodeName.name === nodeName;
    }).length === 0) {
      chartNodes.push({name: nodeName, symbolSize: 1})
    }
  }

}

interface ChartNode {
  name: string,
  symbolSize: number
}
