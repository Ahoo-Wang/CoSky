import { memo } from 'react';
import { Handle, Position, NodeProps } from '@xyflow/react';
import { NodeType, NODE_TYPE_COLORS } from './topologies.ts';

export interface ServiceNodeData {
    label: string;
    nodeType: NodeType;
    inDegree: number;
    outDegree: number;
}

export const ServiceNode = memo(({ data, selected }: NodeProps) => {
    const nodeData = data as unknown as ServiceNodeData;
    const { label, nodeType, inDegree, outDegree } = nodeData;
    
    // Get background color based on node type
    const getBackgroundColor = () => {
        const colors = NODE_TYPE_COLORS[nodeType] || NODE_TYPE_COLORS.source;
        return selected ? colors.borderColor : colors.backgroundColor;
    };

    // Get node icon based on type
    const getIcon = () => {
        switch (nodeType) {
            case 'source':
                return '▶';
            case 'target':
                return '⏸';
            case 'intermediate':
                return '⚡';
            default:
                return '●';
        }
    };

    return (
        <div
            style={{
                padding: '8px 12px',
                borderRadius: '6px',
                background: getBackgroundColor(),
                color: '#fff',
                border: selected ? '2px solid #fff' : '2px solid transparent',
                boxShadow: selected 
                    ? '0 4px 12px rgba(0,0,0,0.3)' 
                    : '0 2px 8px rgba(0,0,0,0.15)',
                minWidth: '150px',
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
                }}
            />
            
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                {/* Icon indicator */}
                <span style={{ fontSize: '14px', opacity: 0.9 }}>
                    {getIcon()}
                </span>
                
                {/* Label with word wrap support */}
                <div style={{ 
                    flex: 1,
                    fontSize: '13px',
                    fontWeight: 500,
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
                opacity: 0.85,
                display: 'flex',
                justifyContent: 'space-between',
            }}>
                <span>In: {inDegree}</span>
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
                }}
            />
        </div>
    );
});

ServiceNode.displayName = 'ServiceNode';
