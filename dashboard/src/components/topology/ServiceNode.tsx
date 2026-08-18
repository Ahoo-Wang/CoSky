import { memo } from 'react';
import type { NodeProps } from '@xyflow/react';
import { Handle, Position } from '@xyflow/react';
import type {ServiceNodeData} from './topologies.ts';
import {NODE_TYPE_COLORS} from './topologies.ts';
import {Circle, Database, Play, Zap} from 'lucide-react';

export const ServiceNode = memo(({ data, selected }: NodeProps) => {
    const nodeData = data as unknown as ServiceNodeData;
    const { label, nodeType, inDegree, outDegree } = nodeData;
    
    const colors = NODE_TYPE_COLORS[nodeType] || NODE_TYPE_COLORS.source;

    // Get node icon based on type
    const getIcon = () => {
        switch (nodeType) {
            case 'source':
                return <Play size={15}/>;
            case 'target':
                return <Database size={15}/>;
            case 'intermediate':
                return <Zap size={15}/>;
            default:
                return <Circle size={15}/>;
        }
    };

    return (
        <div
            style={{
                padding: '8px 12px',
                borderRadius: '10px',
                background: '#fff',
                color: '#262638',
                border: `1.5px solid ${selected ? colors.borderColor : '#dcd9ee'}`,
                boxShadow: selected
                    ? `0 0 0 3px ${colors.backgroundColor}24, 0 8px 20px rgba(26, 24, 55, 0.14)`
                    : '0 4px 12px rgba(26, 24, 55, 0.08)',
                minWidth: '158px',
                transition: 'all 0.2s ease',
                cursor: 'pointer',
            }}
        >
            {/* Input handle for incoming edges */}
            <Handle
                type="target"
                position={Position.Top}
                style={{
                    background: '#fff',
                    width: '8px',
                    height: '8px',
                    border: '2px solid currentColor',
                    opacity: 0,
                }}
            />
            
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                {/* Icon indicator */}
                <span style={{
                    display: 'inline-flex',
                    width: 28,
                    height: 28,
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRadius: 7,
                    color: colors.borderColor,
                    background: `${colors.backgroundColor}18`,
                }}>
                    {getIcon()}
                </span>
                
                {/* Label with word wrap support */}
                <div style={{ 
                    flex: 1,
                    fontSize: '13px',
                    fontWeight: 600,
                    wordWrap: 'break-word',
                    lineHeight: '1.4'
                }}>
                    {label}
                </div>
            </div>
            
            {/* Degree information */}
            <div style={{
                marginTop: '4px',
                fontSize: '11px',
                color: '#77778a',
                display: 'flex',
                justifyContent: 'space-between',
            }}>
                <span className="inline-flex items-center gap-1"><Circle size={7} fill="#22a06b" color="#22a06b"/> In: {inDegree}</span>
                <span>Out: {outDegree}</span>
            </div>
            
            {/* Output handle for outgoing edges */}
            <Handle
                type="source"
                position={Position.Bottom}
                style={{
                    background: '#fff',
                    width: '8px',
                    height: '8px',
                    border: '2px solid currentColor',
                    opacity: 0,
                }}
            />
        </div>
    );
});

ServiceNode.displayName = 'ServiceNode';
