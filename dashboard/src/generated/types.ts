/**
 * - key: ErrorResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "code": {
 *       "type": "string"
 *     },
 *     "msg": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface ErrorResponse {
    code: string;
    msg: string;
}

/**
 * - key: ResourceActionDto
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespace": {
 *       "type": "string"
 *     },
 *     "action": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface ResourceActionDto {
    namespace: string;
    action: string;
}

/**
 * - key: SaveRoleRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "desc": {
 *       "type": "string"
 *     },
 *     "resourceActionBind": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/ResourceActionDto"
 *       },
 *       "uniqueItems": true
 *     }
 *   }
 * }
 * ```
 */
export interface SaveRoleRequest {
    desc: string;
    /**
     * - Array Constraints
     *   - uniqueItems: true
     */
    resourceActionBind: ResourceActionDto[];
}

/**
 * - key: InstanceDto
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "schema": {
 *       "type": "string"
 *     },
 *     "host": {
 *       "type": "string"
 *     },
 *     "port": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "weight": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "ttlAt": {
 *       "type": "integer",
 *       "format": "int64"
 *     },
 *     "metadata": {
 *       "type": "object",
 *       "additionalProperties": {
 *         "type": "string"
 *       }
 *     },
 *     "ephemeral": {
 *       "type": "boolean"
 *     }
 *   }
 * }
 * ```
 *
 */
export interface InstanceDto {
    schema: string;
    host: string;
    /** - format: int32 */
    port: number;
    /** - format: int32 */
    weight: number;
    /** - format: int64 */
    ttlAt: number;
    metadata: Record<string, string>;
    isEphemeral: boolean;
}

/**
 * - key: AddUserRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "password": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface AddUserRequest {
    password: string;
}

/**
 * - key: ImportResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "total": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "succeeded": {
 *       "type": "integer",
 *       "format": "int32"
 *     }
 *   }
 * }
 * ```
 */
export interface ImportResponse {
    /** - format: int32 */
    total: number;
    /** - format: int32 */
    succeeded: number;
}

/**
 * - key: DefaultRefreshTokenCredentials
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "accessToken": {
 *       "type": "string",
 *       "minLength": 1
 *     },
 *     "refreshToken": {
 *       "type": "string",
 *       "minLength": 1
 *     }
 *   },
 *   "required": [
 *     "accessToken",
 *     "refreshToken"
 *   ]
 * }
 * ```
 */
export interface DefaultRefreshTokenCredentials {
    /**
     * - String Constraints
     *   - minLength: 1
     */
    accessToken: string;
    /**
     * - String Constraints
     *   - minLength: 1
     */
    refreshToken: string;
}

/**
 * - key: CompositeToken
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "accessToken": {
 *       "type": "string"
 *     },
 *     "refreshToken": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface CompositeToken {
    accessToken: string;
    refreshToken: string;
}

/**
 * - key: LoginRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "password": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface LoginRequest {
    password: string;
}

/**
 * - key: ChangePwdRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "oldPassword": {
 *       "type": "string"
 *     },
 *     "newPassword": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface ChangePwdRequest {
    oldPassword: string;
    newPassword: string;
}

/**
 * - key: CoSecPrincipal
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "name": {
 *       "type": "string"
 *     },
 *     "id": {
 *       "type": "string"
 *     },
 *     "attributes": {
 *       "type": "object",
 *       "additionalProperties": {}
 *     },
 *     "authenticated": {
 *       "type": "boolean"
 *     },
 *     "anonymous": {
 *       "type": "boolean"
 *     },
 *     "policies": {
 *       "type": "array",
 *       "items": {
 *         "type": "string"
 *       },
 *       "uniqueItems": true
 *     },
 *     "roles": {
 *       "type": "array",
 *       "items": {
 *         "type": "string"
 *       },
 *       "uniqueItems": true
 *     }
 *   }
 * }
 * ```
 */
export interface CoSecPrincipal {
    name: string;
    id: string;
    attributes: Record<string, any>;
    authenticated: boolean;
    anonymous: boolean;
    /**
     * - Array Constraints
     *   - uniqueItems: true
     */
    policies: string[];
    /**
     * - Array Constraints
     *   - uniqueItems: true
     */
    roles: string[];
}

/**
 * - key: RoleDto
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "name": {
 *       "type": "string"
 *     },
 *     "desc": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface RoleDto {
    name: string;
    desc: string;
}

/**
 * - key: GetStatResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespaces": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "services": {
 *       "$ref": "#/components/schemas/Services"
 *     },
 *     "instances": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "configs": {
 *       "type": "integer",
 *       "format": "int32"
 *     }
 *   }
 * }
 * ```
 */
export interface GetStatResponse {
    /** - format: int32 */
    namespaces: number;
    services: Services;
    /** - format: int32 */
    instances: number;
    /** - format: int32 */
    configs: number;
}

/**
 * - key: Services
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "total": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "health": {
 *       "type": "integer",
 *       "format": "int32"
 *     }
 *   }
 * }
 * ```
 */
export interface Services {
    /** - format: int32 */
    total: number;
    /** - format: int32 */
    health: number;
}

/**
 * - key: ServiceInstance
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "ephemeral": {
 *       "type": "boolean"
 *     },
 *     "metadata": {
 *       "type": "object",
 *       "additionalProperties": {
 *         "type": "string"
 *       }
 *     },
 *     "weight": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "ttlAt": {
 *       "type": "integer",
 *       "format": "int64"
 *     },
 *     "expired": {
 *       "type": "boolean"
 *     },
 *     "host": {
 *       "type": "string"
 *     },
 *     "port": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "schema": {
 *       "type": "string"
 *     },
 *     "instanceId": {
 *       "type": "string"
 *     },
 *     "serviceId": {
 *       "type": "string"
 *     },
 *     "uri": {
 *       "type": "string",
 *       "format": "uri"
 *     },
 *     "secure": {
 *       "type": "boolean"
 *     }
 *   }
 * }
 * ```
 */
export interface ServiceInstance {
    isEphemeral: boolean;
    metadata: Record<string, string>;
    /** - format: int32 */
    weight: number;
    /** - format: int64 */
    ttlAt: number;
    isExpired: boolean;
    host: string;
    /** - format: int32 */
    port: number;
    schema: string;
    instanceId: string;
    serviceId: string;
    /** - format: uri */
    uri: string;
    secure: boolean;
}

/**
 * - key: ServiceStat
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "serviceId": {
 *       "type": "string"
 *     },
 *     "instanceCount": {
 *       "type": "integer",
 *       "format": "int32"
 *     }
 *   }
 * }
 * ```
 */
export interface ServiceStat {
    serviceId: string;
    /** - format: int32 */
    instanceCount: number;
}

/**
 * - key: Config
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "data": {
 *       "type": "string"
 *     },
 *     "hash": {
 *       "type": "string"
 *     },
 *     "createTime": {
 *       "type": "integer",
 *       "format": "int64"
 *     },
 *     "version": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "configId": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface Config {
    data: string;
    hash: string;
    /** - format: int64 */
    createTime: number;
    /** - format: int32 */
    version: number;
    configId: string;
}

/**
 * - key: ConfigVersion
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "version": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "configId": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface ConfigVersion {
    /** - format: int32 */
    version: number;
    configId: string;
}

/**
 * - key: ConfigHistory
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "configId": {
 *       "type": "string"
 *     },
 *     "data": {
 *       "type": "string"
 *     },
 *     "hash": {
 *       "type": "string"
 *     },
 *     "createTime": {
 *       "type": "integer",
 *       "format": "int64"
 *     },
 *     "version": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "op": {
 *       "type": "string"
 *     },
 *     "opTime": {
 *       "type": "integer",
 *       "format": "int64"
 *     }
 *   }
 * }
 * ```
 */
export interface ConfigHistory {
    configId: string;
    data: string;
    hash: string;
    /** - format: int64 */
    createTime: number;
    /** - format: int32 */
    version: number;
    op: string;
    /** - format: int64 */
    opTime: number;
}

/**
 * - key: AuditLog
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "operator": {
 *       "type": "string"
 *     },
 *     "ip": {
 *       "type": "string"
 *     },
 *     "resource": {
 *       "type": "string"
 *     },
 *     "action": {
 *       "type": "string"
 *     },
 *     "status": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "msg": {
 *       "type": "string"
 *     },
 *     "opTime": {
 *       "type": "integer",
 *       "format": "int64"
 *     }
 *   }
 * }
 * ```
 */
export interface AuditLog {
    operator: string;
    ip: string;
    resource: string;
    action: string;
    /** - format: int32 */
    status: number;
    msg: string;
    /** - format: int64 */
    opTime: number;
}

/**
 * - key: QueryLogResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "list": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/AuditLog"
 *       }
 *     },
 *     "total": {
 *       "type": "integer",
 *       "format": "int64"
 *     }
 *   }
 * }
 * ```
 */
export interface QueryLogResponse {
    list: AuditLog[];
    /** - format: int64 */
    total: number;
}
