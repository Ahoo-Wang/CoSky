/**
 * - key: AuthConfigResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "oidc": {
 *       "type": "object",
 *       "description": "Omitted when people cannot sign in through OIDC; a ServiceAccount token still works.",
 *       "properties": {
 *         "issuer": {
 *           "type": "string"
 *         },
 *         "clientId": {
 *           "type": "string"
 *         },
 *         "scopes": {
 *           "type": "array",
 *           "items": {
 *             "type": "string"
 *           }
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
export interface AuthConfigResponse {
    /** Omitted when people cannot sign in through OIDC; a ServiceAccount token still works. */
    oidc: {
        issuer: string;
        clientId: string;
        scopes: string[];
    };
}

/**
 * - key: BadRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "violations": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/FieldViolation"
 *       }
 *     }
 *   }
 * }
 * ```
 */
export interface BadRequest {
    violations: FieldViolation[];
}

/**
 * - `CONFIG_FORMAT_UNSPECIFIED`
 * - `CONFIG_FORMAT_YAML`
 * - `CONFIG_FORMAT_PROPERTIES`
 * - `CONFIG_FORMAT_JSON`
 * - `CONFIG_FORMAT_TEXT`
 * - key: ConfigFormat
 * - schema: 
 * ```json
 * {
 *   "type": "string",
 *   "description": "- `CONFIG_FORMAT_UNSPECIFIED`\n- `CONFIG_FORMAT_YAML`\n- `CONFIG_FORMAT_PROPERTIES`\n- `CONFIG_FORMAT_JSON`\n- `CONFIG_FORMAT_TEXT`",
 *   "enum": [
 *     "CONFIG_FORMAT_UNSPECIFIED",
 *     "CONFIG_FORMAT_YAML",
 *     "CONFIG_FORMAT_PROPERTIES",
 *     "CONFIG_FORMAT_JSON",
 *     "CONFIG_FORMAT_TEXT"
 *   ]
 * }
 * ```
 */
export enum ConfigFormat {
    CONFIG_FORMAT_UNSPECIFIED = `CONFIG_FORMAT_UNSPECIFIED`,
    CONFIG_FORMAT_YAML = `CONFIG_FORMAT_YAML`,
    CONFIG_FORMAT_PROPERTIES = `CONFIG_FORMAT_PROPERTIES`,
    CONFIG_FORMAT_JSON = `CONFIG_FORMAT_JSON`,
    CONFIG_FORMAT_TEXT = `CONFIG_FORMAT_TEXT`
}

/**
 * - key: ConfigImportResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "items": {
 *       "type": "array",
 *       "items": {
 *         "type": "object",
 *         "properties": {
 *           "name": {
 *             "type": "string"
 *           },
 *           "revision": {
 *             "type": "string",
 *             "format": "int64"
 *           },
 *           "changed": {
 *             "type": "boolean"
 *           }
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
export interface ConfigImportResponse {
    items: {
        name: string;

        /**
         * - format: int64
         */
        revision: string
        ;
        changed: boolean;
    }[];
}

/**
 * - key: ConfigSpec
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "format": {
 *       "$ref": "#/components/schemas/ConfigFormat",
 *       "description": "declared explicitly, never inferred from the name"
 *     },
 *     "content": {
 *       "type": "string",
 *       "description": "<= 1 MiB"
 *     },
 *     "sha256": {
 *       "type": "string",
 *       "description": "computed by the server, read-only"
 *     }
 *   }
 * }
 * ```
 */
export interface ConfigSpec {
    /** declared explicitly, never inferred from the name */
    format: ConfigFormat;
    /** <= 1 MiB */
    content: string;
    /** computed by the server, read-only */
    sha256: string;
}

/**
 * - key: CreateTokenRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "expiresAt": {
 *       "type": "string",
 *       "format": "date-time",
 *       "description": "Expiry; omitted means the token does not expire."
 *     }
 *   }
 * }
 * ```
 */
export interface CreateTokenRequest {
    /**
     * Expiry; omitted means the token does not expire.
     * - format: date-time
     */
    expiresAt: string;
}

/**
 * - key: CreateTokenResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "id": {
 *       "type": "string"
 *     },
 *     "token": {
 *       "type": "string",
 *       "description": "Plaintext `cosky_<tokenId>_<secret>`, returned only once."
 *     },
 *     "expiresAt": {
 *       "type": "string",
 *       "format": "date-time"
 *     }
 *   }
 * }
 * ```
 */
export interface CreateTokenResponse {
    id: string;
    /** Plaintext `cosky_<tokenId>_<secret>`, returned only once. */
    token: string;
    /** - format: date-time */
    expiresAt: string;
}

/**
 * - key: DeleteRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "key": {
 *       "$ref": "#/components/schemas/ResourceKey"
 *     },
 *     "expectedRevision": {
 *       "type": "string",
 *       "format": "int64"
 *     }
 *   }
 * }
 * ```
 */
export interface DeleteRequest {
    key: ResourceKey;
    /** - format: int64 */
    expectedRevision: string;
}

/**
 * - key: DeleteResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "revision": {
 *       "type": "string",
 *       "format": "int64"
 *     },
 *     "deleted": {
 *       "type": "boolean"
 *     }
 *   }
 * }
 * ```
 */
export interface DeleteResponse {
    /** - format: int64 */
    revision: string;
    deleted: boolean;
}

/**
 * - key: Endpoint
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "scheme": {
 *       "type": "string",
 *       "description": "http / https / grpc ..."
 *     },
 *     "host": {
 *       "type": "string"
 *     },
 *     "port": {
 *       "type": "integer",
 *       "format": "int32"
 *     }
 *   }
 * }
 * ```
 */
export interface Endpoint {
    /** http / https / grpc ... */
    scheme: string;
    host: string;
    /** - format: int32 */
    port: number;
}

/**
 * At most one of `resourceNotFound`, `namespace`, `revisionConflict`, `leaseNotFound`, `badRequest`, `watchQuotaExceeded` is set (oneof `detail`).
 * - key: ErrorDetail
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "description": "At most one of `resourceNotFound`, `namespace`, `revisionConflict`, `leaseNotFound`, `badRequest`, `watchQuotaExceeded` is set (oneof `detail`).",
 *   "properties": {
 *     "reason": {
 *       "$ref": "#/components/schemas/ErrorReason"
 *     },
 *     "resourceNotFound": {
 *       "$ref": "#/components/schemas/ResourceNotFound"
 *     },
 *     "namespace": {
 *       "$ref": "#/components/schemas/NamespaceRef",
 *       "description": "NAMESPACE_NOT_FOUND, NAMESPACE_NOT_EMPTY, MIN_REVISION_TIMEOUT, REVISION_REGRESSION"
 *     },
 *     "revisionConflict": {
 *       "$ref": "#/components/schemas/RevisionConflict"
 *     },
 *     "leaseNotFound": {
 *       "$ref": "#/components/schemas/LeaseNotFound"
 *     },
 *     "badRequest": {
 *       "$ref": "#/components/schemas/BadRequest"
 *     },
 *     "watchQuotaExceeded": {
 *       "$ref": "#/components/schemas/WatchQuotaExceeded"
 *     }
 *   }
 * }
 * ```
 */
export interface ErrorDetail {
    reason: ErrorReason;
    resourceNotFound: ResourceNotFound;
    /** NAMESPACE_NOT_FOUND, NAMESPACE_NOT_EMPTY, MIN_REVISION_TIMEOUT, REVISION_REGRESSION */
    namespace: NamespaceRef;
    revisionConflict: RevisionConflict;
    leaseNotFound: LeaseNotFound;
    badRequest: BadRequest;
    watchQuotaExceeded: WatchQuotaExceeded;
}

/**
 * Machine-readable reason; the gRPC code and HTTP status follow from it (see me.ahoo.cosky.api.error.ErrorCodes).
 *
 * - `ERROR_REASON_UNSPECIFIED`
 * - `ERROR_REASON_RESOURCE_NOT_FOUND`: NOT_FOUND / 404
 * - `ERROR_REASON_NAMESPACE_NOT_FOUND`: NOT_FOUND / 404 (§7.2)
 * - `ERROR_REASON_REVISION_CONFLICT`: ABORTED / 409
 * - `ERROR_REASON_LEASE_NOT_FOUND`: FAILED_PRECONDITION / 412
 * - `ERROR_REASON_NAMESPACE_NOT_EMPTY`: FAILED_PRECONDITION / 409 (§7.2)
 * - `ERROR_REASON_UNAUTHENTICATED`: UNAUTHENTICATED / 401
 * - `ERROR_REASON_PERMISSION_DENIED`: PERMISSION_DENIED / 403
 * - `ERROR_REASON_INVALID_ARGUMENT`: INVALID_ARGUMENT / 400
 * - `ERROR_REASON_MIN_REVISION_TIMEOUT`: DEADLINE_EXCEEDED / 504
 * - `ERROR_REASON_STORE_UNAVAILABLE`: UNAVAILABLE / 503
 * - `ERROR_REASON_REVISION_REGRESSION`: UNAVAILABLE / 503: the namespace diverged and is reloading (§6.4)
 * - `ERROR_REASON_WATCH_QUOTA_EXCEEDED`: RESOURCE_EXHAUSTED / 429: too many watches on one stream (§7.3)
 * - key: ErrorReason
 * - schema: 
 * ```json
 * {
 *   "type": "string",
 *   "description": "Machine-readable reason; the gRPC code and HTTP status follow from it (see me.ahoo.cosky.api.error.ErrorCodes).\n\n- `ERROR_REASON_UNSPECIFIED`\n- `ERROR_REASON_RESOURCE_NOT_FOUND`: NOT_FOUND / 404\n- `ERROR_REASON_NAMESPACE_NOT_FOUND`: NOT_FOUND / 404 (§7.2)\n- `ERROR_REASON_REVISION_CONFLICT`: ABORTED / 409\n- `ERROR_REASON_LEASE_NOT_FOUND`: FAILED_PRECONDITION / 412\n- `ERROR_REASON_NAMESPACE_NOT_EMPTY`: FAILED_PRECONDITION / 409 (§7.2)\n- `ERROR_REASON_UNAUTHENTICATED`: UNAUTHENTICATED / 401\n- `ERROR_REASON_PERMISSION_DENIED`: PERMISSION_DENIED / 403\n- `ERROR_REASON_INVALID_ARGUMENT`: INVALID_ARGUMENT / 400\n- `ERROR_REASON_MIN_REVISION_TIMEOUT`: DEADLINE_EXCEEDED / 504\n- `ERROR_REASON_STORE_UNAVAILABLE`: UNAVAILABLE / 503\n- `ERROR_REASON_REVISION_REGRESSION`: UNAVAILABLE / 503: the namespace diverged and is reloading (§6.4)\n- `ERROR_REASON_WATCH_QUOTA_EXCEEDED`: RESOURCE_EXHAUSTED / 429: too many watches on one stream (§7.3)",
 *   "enum": [
 *     "ERROR_REASON_UNSPECIFIED",
 *     "ERROR_REASON_RESOURCE_NOT_FOUND",
 *     "ERROR_REASON_NAMESPACE_NOT_FOUND",
 *     "ERROR_REASON_REVISION_CONFLICT",
 *     "ERROR_REASON_LEASE_NOT_FOUND",
 *     "ERROR_REASON_NAMESPACE_NOT_EMPTY",
 *     "ERROR_REASON_UNAUTHENTICATED",
 *     "ERROR_REASON_PERMISSION_DENIED",
 *     "ERROR_REASON_INVALID_ARGUMENT",
 *     "ERROR_REASON_MIN_REVISION_TIMEOUT",
 *     "ERROR_REASON_STORE_UNAVAILABLE",
 *     "ERROR_REASON_REVISION_REGRESSION",
 *     "ERROR_REASON_WATCH_QUOTA_EXCEEDED"
 *   ]
 * }
 * ```
 */
export enum ErrorReason {
    ERROR_REASON_UNSPECIFIED = `ERROR_REASON_UNSPECIFIED`,
    ERROR_REASON_RESOURCE_NOT_FOUND = `ERROR_REASON_RESOURCE_NOT_FOUND`,
    ERROR_REASON_NAMESPACE_NOT_FOUND = `ERROR_REASON_NAMESPACE_NOT_FOUND`,
    ERROR_REASON_REVISION_CONFLICT = `ERROR_REASON_REVISION_CONFLICT`,
    ERROR_REASON_LEASE_NOT_FOUND = `ERROR_REASON_LEASE_NOT_FOUND`,
    ERROR_REASON_NAMESPACE_NOT_EMPTY = `ERROR_REASON_NAMESPACE_NOT_EMPTY`,
    ERROR_REASON_UNAUTHENTICATED = `ERROR_REASON_UNAUTHENTICATED`,
    ERROR_REASON_PERMISSION_DENIED = `ERROR_REASON_PERMISSION_DENIED`,
    ERROR_REASON_INVALID_ARGUMENT = `ERROR_REASON_INVALID_ARGUMENT`,
    ERROR_REASON_MIN_REVISION_TIMEOUT = `ERROR_REASON_MIN_REVISION_TIMEOUT`,
    ERROR_REASON_STORE_UNAVAILABLE = `ERROR_REASON_STORE_UNAVAILABLE`,
    ERROR_REASON_REVISION_REGRESSION = `ERROR_REASON_REVISION_REGRESSION`,
    ERROR_REASON_WATCH_QUOTA_EXCEEDED = `ERROR_REASON_WATCH_QUOTA_EXCEEDED`
}

/**
 * - key: ErrorResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "status": {
 *       "type": "string",
 *       "description": "gRPC status code name, e.g. `ABORTED`."
 *     },
 *     "message": {
 *       "type": "string"
 *     },
 *     "detail": {
 *       "$ref": "#/components/schemas/ErrorDetail"
 *     }
 *   }
 * }
 * ```
 */
export interface ErrorResponse {
    /** gRPC status code name, e.g. `ABORTED`. */
    status: string;
    message: string;
    detail: ErrorDetail;
}

/**
 * - key: FieldViolation
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "field": {
 *       "type": "string",
 *       "description": "path in proto field names, e.g. \"resource.meta.labels[\\\"team\\\"]\""
 *     },
 *     "code": {
 *       "$ref": "#/components/schemas/ViolationCode"
 *     },
 *     "description": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface FieldViolation {
    /** path in proto field names, e.g. "resource.meta.labels[\"team\"]" */
    field: string;
    code: ViolationCode;
    description: string;
}

/**
 * - key: GetRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "key": {
 *       "$ref": "#/components/schemas/ResourceKey"
 *     },
 *     "minRevision": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "read-your-writes"
 *     }
 *   }
 * }
 * ```
 */
export interface GetRequest {
    key: ResourceKey;
    /**
     * read-your-writes
     * - format: int64
     */
    minRevision: string;
}

/**
 * - key: GrantRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespace": {
 *       "type": "string"
 *     },
 *     "ttlSeconds": {
 *       "type": "integer",
 *       "format": "int32",
 *       "description": "0 = default (30); otherwise within [5, 300]"
 *     }
 *   }
 * }
 * ```
 */
export interface GrantRequest {
    namespace: string;
    /**
     * 0 = default (30); otherwise within [5, 300]
     * - format: int32
     */
    ttlSeconds: number;
}

/**
 * - key: GrantResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "lease": {
 *       "type": "string",
 *       "format": "int64"
 *     },
 *     "ttlSeconds": {
 *       "type": "integer",
 *       "format": "int32"
 *     }
 *   }
 * }
 * ```
 */
export interface GrantResponse {
    /** - format: int64 */
    lease: string;
    /** - format: int32 */
    ttlSeconds: number;
}

/**
 * - key: HistoryRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "key": {
 *       "$ref": "#/components/schemas/ResourceKey"
 *     },
 *     "limit": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "pageToken": {
 *       "type": "string",
 *       "description": "next_page_token of the previous page; empty = the first page"
 *     }
 *   }
 * }
 * ```
 */
export interface HistoryRequest {
    key: ResourceKey;
    /** - format: int32 */
    limit: number;
    /** next_page_token of the previous page; empty = the first page */
    pageToken: string;
}

/**
 * - key: HistoryResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "revisions": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/Resource"
 *       },
 *       "description": "newest first"
 *     },
 *     "nextPageToken": {
 *       "type": "string",
 *       "description": "empty = the last page"
 *     }
 *   }
 * }
 * ```
 */
export interface HistoryResponse {
    /** newest first */
    revisions: Resource[];
    /** empty = the last page */
    nextPageToken: string;
}

/**
 * - key: InstanceSpec
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "service": {
 *       "type": "string"
 *     },
 *     "endpoint": {
 *       "$ref": "#/components/schemas/Endpoint"
 *     },
 *     "weight": {
 *       "type": "integer",
 *       "format": "int32",
 *       "description": "unset = 1 (filled by the server); 0 = drained but still registered"
 *     },
 *     "metadata": {
 *       "type": "object",
 *       "additionalProperties": {
 *         "type": "string"
 *       },
 *       "description": "user data, kept apart from system fields; <= 64 entries"
 *     }
 *   }
 * }
 * ```
 */
export interface InstanceSpec {
    service: string;
    endpoint: Endpoint;
    /**
     * unset = 1 (filled by the server); 0 = drained but still registered
     * - format: int32
     */
    weight: number;
    /** user data, kept apart from system fields; <= 64 entries */
    metadata: Record<string, string>;
}

/**
 * - key: KeepAliveRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespace": {
 *       "type": "string"
 *     },
 *     "lease": {
 *       "type": "string",
 *       "format": "int64"
 *     }
 *   }
 * }
 * ```
 */
export interface KeepAliveRequest {
    namespace: string;
    /** - format: int64 */
    lease: string;
}

/**
 * - key: KeepAliveResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "lease": {
 *       "type": "string",
 *       "format": "int64"
 *     },
 *     "ttlSeconds": {
 *       "type": "integer",
 *       "format": "int32",
 *       "description": "0 = the lease no longer exists"
 *     },
 *     "attachedCount": {
 *       "type": "integer",
 *       "format": "int32",
 *       "description": "Instances on the lease; fewer than the client registered means a failover lost some, and it should put them back"
 *     }
 *   }
 * }
 * ```
 */
export interface KeepAliveResponse {
    /** - format: int64 */
    lease: string;
    /**
     * 0 = the lease no longer exists
     * - format: int32
     */
    ttlSeconds: number;
    /**
     * Instances on the lease; fewer than the client registered means a failover lost some, and it should put them back
     * - format: int32
     */
    attachedCount: number;
}

/**
 * - `KIND_UNSPECIFIED`
 * - `KIND_NAMESPACE`
 * - `KIND_CONFIG`
 * - `KIND_INSTANCE`
 * - `KIND_SERVICE_ACCOUNT`
 * - `KIND_ROLE`
 * - `KIND_ROLE_BINDING`
 * - key: Kind
 * - schema: 
 * ```json
 * {
 *   "type": "string",
 *   "description": "- `KIND_UNSPECIFIED`\n- `KIND_NAMESPACE`\n- `KIND_CONFIG`\n- `KIND_INSTANCE`\n- `KIND_SERVICE_ACCOUNT`\n- `KIND_ROLE`\n- `KIND_ROLE_BINDING`",
 *   "enum": [
 *     "KIND_UNSPECIFIED",
 *     "KIND_NAMESPACE",
 *     "KIND_CONFIG",
 *     "KIND_INSTANCE",
 *     "KIND_SERVICE_ACCOUNT",
 *     "KIND_ROLE",
 *     "KIND_ROLE_BINDING"
 *   ]
 * }
 * ```
 */
export enum Kind {
    KIND_UNSPECIFIED = `KIND_UNSPECIFIED`,
    KIND_NAMESPACE = `KIND_NAMESPACE`,
    KIND_CONFIG = `KIND_CONFIG`,
    KIND_INSTANCE = `KIND_INSTANCE`,
    KIND_SERVICE_ACCOUNT = `KIND_SERVICE_ACCOUNT`,
    KIND_ROLE = `KIND_ROLE`,
    KIND_ROLE_BINDING = `KIND_ROLE_BINDING`
}

/**
 * - key: LeaseNotFound
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespace": {
 *       "type": "string"
 *     },
 *     "lease": {
 *       "type": "string",
 *       "format": "int64"
 *     }
 *   }
 * }
 * ```
 */
export interface LeaseNotFound {
    namespace: string;
    /** - format: int64 */
    lease: string;
}

/**
 * - key: ListRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespace": {
 *       "type": "string"
 *     },
 *     "kind": {
 *       "$ref": "#/components/schemas/Kind"
 *     },
 *     "labelSelector": {
 *       "type": "object",
 *       "additionalProperties": {
 *         "type": "string"
 *       },
 *       "description": "equality only, all conditions ANDed"
 *     },
 *     "service": {
 *       "type": "string",
 *       "description": "Instance filter"
 *     },
 *     "minRevision": {
 *       "type": "string",
 *       "format": "int64"
 *     },
 *     "pageSize": {
 *       "type": "integer",
 *       "format": "int32",
 *       "description": "0 = default (500); at most 1000; a page may hold fewer to stay within the size cap (§4.6)"
 *     },
 *     "pageToken": {
 *       "type": "string",
 *       "description": "next_page_token of the previous page; empty = the first page"
 *     }
 *   }
 * }
 * ```
 */
export interface ListRequest {
    namespace: string;
    kind: Kind;
    /** equality only, all conditions ANDed */
    labelSelector: Record<string, string>;
    /** Instance filter */
    service: string;
    /** - format: int64 */
    minRevision: string;
    /**
     * 0 = default (500); at most 1000; a page may hold fewer to stay within the size cap (§4.6)
     * - format: int32
     */
    pageSize: number;
    /** next_page_token of the previous page; empty = the first page */
    pageToken: string;
}

/**
 * - key: ListResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "items": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/Resource"
 *       }
 *     },
 *     "revision": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "of the first page, on every page; Watch from it to converge (§4.6)"
 *     },
 *     "epoch": {
 *       "type": "string",
 *       "description": "the timeline of revision; revisions compare only within one epoch (§6.4)"
 *     },
 *     "nextPageToken": {
 *       "type": "string",
 *       "description": "empty = the last page"
 *     }
 *   }
 * }
 * ```
 */
export interface ListResponse {
    items: Resource[];
    /**
     * of the first page, on every page; Watch from it to converge (§4.6)
     * - format: int64
     */
    revision: string;
    /** the timeline of revision; revisions compare only within one epoch (§6.4) */
    epoch: string;
    /** empty = the last page */
    nextPageToken: string;
}

/**
 * - key: MeResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "subject": {
 *       "$ref": "#/components/schemas/Subject"
 *     },
 *     "groups": {
 *       "type": "array",
 *       "items": {
 *         "type": "string"
 *       },
 *       "description": "OIDC groups of a user."
 *     },
 *     "rules": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/Rule"
 *       },
 *       "description": "Union of the rules of every role bound to the subject or its groups."
 *     }
 *   }
 * }
 * ```
 */
export interface MeResponse {
    subject: Subject;
    /** OIDC groups of a user. */
    groups: string[];
    /** Union of the rules of every role bound to the subject or its groups. */
    rules: Rule[];
}

/**
 * - key: NamespaceRef
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespace": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface NamespaceRef {
    namespace: string;
}

/**
 * - key: NamespaceSpec
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "description": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface NamespaceSpec {
    description: string;
}

/**
 * - key: ObjectMeta
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespace": {
 *       "type": "string",
 *       "description": "\"_system\" for Namespace / ServiceAccount / Role / RoleBinding"
 *     },
 *     "name": {
 *       "type": "string"
 *     },
 *     "revision": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "namespace revision that last modified it (read-only)"
 *     },
 *     "createRevision": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "read-only"
 *     },
 *     "labels": {
 *       "type": "object",
 *       "additionalProperties": {
 *         "type": "string"
 *       }
 *     },
 *     "createdAt": {
 *       "type": "string",
 *       "format": "date-time",
 *       "description": "read-only"
 *     },
 *     "updatedAt": {
 *       "type": "string",
 *       "format": "date-time",
 *       "description": "read-only"
 *     },
 *     "updatedBy": {
 *       "type": "string",
 *       "description": "read-only; identity of the writer, the audit trail"
 *     },
 *     "lease": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "settable on Instance only"
 *     }
 *   }
 * }
 * ```
 */
export interface ObjectMeta {
    /** "_system" for Namespace / ServiceAccount / Role / RoleBinding */
    namespace: string;
    name: string;
    /**
     * namespace revision that last modified it (read-only)
     * - format: int64
     */
    revision: string;
    /**
     * read-only
     * - format: int64
     */
    createRevision: string;
    labels: Record<string, string>;
    /**
     * read-only
     * - format: date-time
     */
    createdAt: string;
    /**
     * read-only
     * - format: date-time
     */
    updatedAt: string;
    /** read-only; identity of the writer, the audit trail */
    updatedBy: string;
    /**
     * settable on Instance only
     * - format: int64
     */
    lease: string;
}

/**
 * - key: PutRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "resource": {
 *       "$ref": "#/components/schemas/Resource"
 *     },
 *     "expectedRevision": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "0 = no check; -1 = must not exist; > 0 = compare-and-set"
 *     }
 *   }
 * }
 * ```
 */
export interface PutRequest {
    resource: Resource;
    /**
     * 0 = no check; -1 = must not exist; > 0 = compare-and-set
     * - format: int64
     */
    expectedRevision: string;
}

/**
 * - key: PutResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "revision": {
 *       "type": "string",
 *       "format": "int64"
 *     },
 *     "changed": {
 *       "type": "boolean",
 *       "description": "false when the content is unchanged: no new revision, no event"
 *     }
 *   }
 * }
 * ```
 */
export interface PutResponse {
    /** - format: int64 */
    revision: string;
    /** false when the content is unchanged: no new revision, no event */
    changed: boolean;
}

/**
 * At most one of `namespace`, `config`, `instance`, `serviceAccount`, `role`, `roleBinding` is set (oneof `spec`).
 * - key: Resource
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "description": "At most one of `namespace`, `config`, `instance`, `serviceAccount`, `role`, `roleBinding` is set (oneof `spec`).",
 *   "properties": {
 *     "meta": {
 *       "$ref": "#/components/schemas/ObjectMeta"
 *     },
 *     "namespace": {
 *       "$ref": "#/components/schemas/NamespaceSpec"
 *     },
 *     "config": {
 *       "$ref": "#/components/schemas/ConfigSpec"
 *     },
 *     "instance": {
 *       "$ref": "#/components/schemas/InstanceSpec"
 *     },
 *     "serviceAccount": {
 *       "$ref": "#/components/schemas/ServiceAccountSpec"
 *     },
 *     "role": {
 *       "$ref": "#/components/schemas/RoleSpec"
 *     },
 *     "roleBinding": {
 *       "$ref": "#/components/schemas/RoleBindingSpec"
 *     }
 *   }
 * }
 * ```
 */
export interface Resource {
    meta: ObjectMeta;
    namespace: NamespaceSpec;
    config: ConfigSpec;
    instance: InstanceSpec;
    serviceAccount: ServiceAccountSpec;
    role: RoleSpec;
    roleBinding: RoleBindingSpec;
}

/**
 * Identifies one resource; global kinds use the "_system" namespace.
 * - key: ResourceKey
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "description": "Identifies one resource; global kinds use the \"_system\" namespace.",
 *   "properties": {
 *     "namespace": {
 *       "type": "string"
 *     },
 *     "kind": {
 *       "$ref": "#/components/schemas/Kind"
 *     },
 *     "name": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface ResourceKey {
    namespace: string;
    kind: Kind;
    name: string;
}

/**
 * - key: ResourceNotFound
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "key": {
 *       "$ref": "#/components/schemas/ResourceKey"
 *     }
 *   }
 * }
 * ```
 */
export interface ResourceNotFound {
    key: ResourceKey;
}

/**
 * - key: RevisionConflict
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "key": {
 *       "$ref": "#/components/schemas/ResourceKey"
 *     },
 *     "expectedRevision": {
 *       "type": "string",
 *       "format": "int64"
 *     },
 *     "currentRevision": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "0 = the resource does not exist"
 *     }
 *   }
 * }
 * ```
 */
export interface RevisionConflict {
    key: ResourceKey;
    /** - format: int64 */
    expectedRevision: string;
    /**
     * 0 = the resource does not exist
     * - format: int64
     */
    currentRevision: string;
}

/**
 * - key: RevokeRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespace": {
 *       "type": "string"
 *     },
 *     "lease": {
 *       "type": "string",
 *       "format": "int64"
 *     }
 *   }
 * }
 * ```
 */
export interface RevokeRequest {
    namespace: string;
    /** - format: int64 */
    lease: string;
}

/**
 * - key: RevokeResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object"
 * }
 * ```
 */
export type RevokeResponse = Record<string, any>;

/**
 * - key: RoleBindingSpec
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "role": {
 *       "type": "string"
 *     },
 *     "subjects": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/Subject"
 *       }
 *     },
 *     "namespaces": {
 *       "type": "array",
 *       "items": {
 *         "type": "string"
 *       },
 *       "description": "Required. The grant applies only in these namespaces, intersected with Rule.namespaces; \"*\" adds no restriction."
 *     }
 *   }
 * }
 * ```
 */
export interface RoleBindingSpec {
    role: string;
    subjects: Subject[];
    /** Required. The grant applies only in these namespaces, intersected with Rule.namespaces; "*" adds no restriction. */
    namespaces: string[];
}

/**
 * - key: RoleSpec
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "description": {
 *       "type": "string"
 *     },
 *     "rules": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/Rule"
 *       }
 *     }
 *   }
 * }
 * ```
 */
export interface RoleSpec {
    description: string;
    rules: Rule[];
}

/**
 * - key: RollbackRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "required": [
 *     "toRevision"
 *   ],
 *   "properties": {
 *     "toRevision": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "A revision from the Config's history."
 *     }
 *   }
 * }
 * ```
 */
export interface RollbackRequest {
    /**
     * A revision from the Config's history.
     * - format: int64
     */
    toRevision: string;
}

/**
 * - key: Rule
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "kinds": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/Kind"
 *       }
 *     },
 *     "namespaces": {
 *       "type": "array",
 *       "items": {
 *         "type": "string"
 *       },
 *       "description": "\"*\" matches every namespace"
 *     },
 *     "verbs": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/Verb"
 *       }
 *     },
 *     "names": {
 *       "type": "array",
 *       "items": {
 *         "type": "string"
 *       },
 *       "description": "Empty matches every object. Otherwise exact values, or prefixes when they end in \"*\", matched against the Config\n name, the Instance spec.service and the object name of other kinds (§7.8)."
 *     }
 *   }
 * }
 * ```
 */
export interface Rule {
    kinds: Kind[];
    /** "*" matches every namespace */
    namespaces: string[];
    verbs: Verb[];
    /**
     * Empty matches every object. Otherwise exact values, or prefixes when they end in "*", matched against the Config
     *  name, the Instance spec.service and the object name of other kinds (§7.8).
     */
    names: string[];
}

/**
 * - key: ServiceAccountSpec
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "description": {
 *       "type": "string"
 *     },
 *     "tokens": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/TokenRef"
 *       },
 *       "description": "at most 2, for rotation"
 *     }
 *   }
 * }
 * ```
 */
export interface ServiceAccountSpec {
    description: string;
    /** at most 2, for rotation */
    tokens: TokenRef[];
}

/**
 * A service is the group of instances sharing InstanceSpec.service.
 * - key: ServiceSummary
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "description": "A service is the group of instances sharing InstanceSpec.service.",
 *   "properties": {
 *     "service": {
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
export interface ServiceSummary {
    service: string;
    /** - format: int32 */
    instanceCount: number;
}

/**
 * - key: ServicesRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespace": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface ServicesRequest {
    namespace: string;
}

/**
 * - key: ServicesResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "services": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/ServiceSummary"
 *       },
 *       "description": "ordered by service name"
 *     },
 *     "revision": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "namespace revision the view was computed at"
 *     }
 *   }
 * }
 * ```
 */
export interface ServicesResponse {
    /** ordered by service name */
    services: ServiceSummary[];
    /**
     * namespace revision the view was computed at
     * - format: int64
     */
    revision: string;
}

/**
 * - key: StatsRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespace": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface StatsRequest {
    namespace: string;
}

/**
 * - key: StatsResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "serviceCount": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "instanceCount": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "configCount": {
 *       "type": "integer",
 *       "format": "int32"
 *     },
 *     "revision": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "namespace revision the counts were computed at"
 *     }
 *   }
 * }
 * ```
 */
export interface StatsResponse {
    /** - format: int32 */
    serviceCount: number;
    /** - format: int32 */
    instanceCount: number;
    /** - format: int32 */
    configCount: number;
    /**
     * namespace revision the counts were computed at
     * - format: int64
     */
    revision: string;
}

/**
 * - key: Subject
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "type": {
 *       "$ref": "#/components/schemas/Subject.Type"
 *     },
 *     "name": {
 *       "type": "string",
 *       "description": "USER / GROUP come from OIDC claims"
 *     }
 *   }
 * }
 * ```
 */
export interface Subject {
    type: SubjectType;
    /** USER / GROUP come from OIDC claims */
    name: string;
}

/**
 * - `TYPE_UNSPECIFIED`
 * - `TYPE_SERVICE_ACCOUNT`
 * - `TYPE_USER`
 * - `TYPE_GROUP`
 * - key: Subject.Type
 * - schema: 
 * ```json
 * {
 *   "type": "string",
 *   "description": "- `TYPE_UNSPECIFIED`\n- `TYPE_SERVICE_ACCOUNT`\n- `TYPE_USER`\n- `TYPE_GROUP`",
 *   "enum": [
 *     "TYPE_UNSPECIFIED",
 *     "TYPE_SERVICE_ACCOUNT",
 *     "TYPE_USER",
 *     "TYPE_GROUP"
 *   ]
 * }
 * ```
 */
export enum SubjectType {
    TYPE_UNSPECIFIED = `TYPE_UNSPECIFIED`,
    TYPE_SERVICE_ACCOUNT = `TYPE_SERVICE_ACCOUNT`,
    TYPE_USER = `TYPE_USER`,
    TYPE_GROUP = `TYPE_GROUP`
}

/**
 * - key: TokenRef
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "id": {
 *       "type": "string"
 *     },
 *     "sha256": {
 *       "type": "string",
 *       "description": "only the hash is stored; the plaintext is returned once, on creation"
 *     },
 *     "expiresAt": {
 *       "type": "string",
 *       "format": "date-time"
 *     }
 *   }
 * }
 * ```
 */
export interface TokenRef {
    id: string;
    /** only the hash is stored; the plaintext is returned once, on creation */
    sha256: string;
    /** - format: date-time */
    expiresAt: string;
}

/**
 * - key: TopologyEdge
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "consumer": {
 *       "type": "string",
 *       "description": "the name the client gave itself, or else the authenticated subject (design D30)"
 *     },
 *     "producer": {
 *       "type": "string",
 *       "description": "watched service"
 *     }
 *   }
 * }
 * ```
 */
export interface TopologyEdge {
    /** the name the client gave itself, or else the authenticated subject (design D30) */
    consumer: string;
    /** watched service */
    producer: string;
}

/**
 * - key: TopologyRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "namespace": {
 *       "type": "string"
 *     }
 *   }
 * }
 * ```
 */
export interface TopologyRequest {
    namespace: string;
}

/**
 * Edges are recorded from live watches: a client watching the instances of one service consumes it (§6.3).
 * - key: TopologyResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "description": "Edges are recorded from live watches: a client watching the instances of one service consumes it (§6.3).",
 *   "properties": {
 *     "edges": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/TopologyEdge"
 *       }
 *     }
 *   }
 * }
 * ```
 */
export interface TopologyResponse {
    edges: TopologyEdge[];
}

/**
 * - `VERB_UNSPECIFIED`
 * - `VERB_GET`: includes List
 * - `VERB_WATCH`
 * - `VERB_PUT`
 * - `VERB_DELETE`
 * - key: Verb
 * - schema: 
 * ```json
 * {
 *   "type": "string",
 *   "description": "- `VERB_UNSPECIFIED`\n- `VERB_GET`: includes List\n- `VERB_WATCH`\n- `VERB_PUT`\n- `VERB_DELETE`",
 *   "enum": [
 *     "VERB_UNSPECIFIED",
 *     "VERB_GET",
 *     "VERB_WATCH",
 *     "VERB_PUT",
 *     "VERB_DELETE"
 *   ]
 * }
 * ```
 */
export enum Verb {
    VERB_UNSPECIFIED = `VERB_UNSPECIFIED`,
    VERB_GET = `VERB_GET`,
    VERB_WATCH = `VERB_WATCH`,
    VERB_PUT = `VERB_PUT`,
    VERB_DELETE = `VERB_DELETE`
}

/**
 * - `VIOLATION_CODE_UNSPECIFIED`
 * - `VIOLATION_CODE_REQUIRED`: missing or empty
 * - `VIOLATION_CODE_INVALID_FORMAT`: does not match the naming rule
 * - `VIOLATION_CODE_RESERVED_NAME`: the name is reserved, e.g. "_system"
 * - `VIOLATION_CODE_TOO_LONG`: exceeds a length or size limit
 * - `VIOLATION_CODE_TOO_MANY`: exceeds a count limit
 * - `VIOLATION_CODE_OUT_OF_RANGE`: a number outside its allowed range
 * - `VIOLATION_CODE_NOT_ALLOWED`: not allowed for this kind or in this context
 * - key: ViolationCode
 * - schema: 
 * ```json
 * {
 *   "type": "string",
 *   "description": "- `VIOLATION_CODE_UNSPECIFIED`\n- `VIOLATION_CODE_REQUIRED`: missing or empty\n- `VIOLATION_CODE_INVALID_FORMAT`: does not match the naming rule\n- `VIOLATION_CODE_RESERVED_NAME`: the name is reserved, e.g. \"_system\"\n- `VIOLATION_CODE_TOO_LONG`: exceeds a length or size limit\n- `VIOLATION_CODE_TOO_MANY`: exceeds a count limit\n- `VIOLATION_CODE_OUT_OF_RANGE`: a number outside its allowed range\n- `VIOLATION_CODE_NOT_ALLOWED`: not allowed for this kind or in this context",
 *   "enum": [
 *     "VIOLATION_CODE_UNSPECIFIED",
 *     "VIOLATION_CODE_REQUIRED",
 *     "VIOLATION_CODE_INVALID_FORMAT",
 *     "VIOLATION_CODE_RESERVED_NAME",
 *     "VIOLATION_CODE_TOO_LONG",
 *     "VIOLATION_CODE_TOO_MANY",
 *     "VIOLATION_CODE_OUT_OF_RANGE",
 *     "VIOLATION_CODE_NOT_ALLOWED"
 *   ]
 * }
 * ```
 */
export enum ViolationCode {
    VIOLATION_CODE_UNSPECIFIED = `VIOLATION_CODE_UNSPECIFIED`,
    VIOLATION_CODE_REQUIRED = `VIOLATION_CODE_REQUIRED`,
    VIOLATION_CODE_INVALID_FORMAT = `VIOLATION_CODE_INVALID_FORMAT`,
    VIOLATION_CODE_RESERVED_NAME = `VIOLATION_CODE_RESERVED_NAME`,
    VIOLATION_CODE_TOO_LONG = `VIOLATION_CODE_TOO_LONG`,
    VIOLATION_CODE_TOO_MANY = `VIOLATION_CODE_TOO_MANY`,
    VIOLATION_CODE_OUT_OF_RANGE = `VIOLATION_CODE_OUT_OF_RANGE`,
    VIOLATION_CODE_NOT_ALLOWED = `VIOLATION_CODE_NOT_ALLOWED`
}

/**
 * - key: WatchQuotaExceeded
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "limit": {
 *       "type": "integer",
 *       "format": "int32",
 *       "description": "watches allowed on one stream"
 *     }
 *   }
 * }
 * ```
 */
export interface WatchQuotaExceeded {
    /**
     * watches allowed on one stream
     * - format: int32
     */
    limit: number;
}

/**
 * At most one of `create`, `cancel` is set (oneof `request`).
 * - key: WatchRequest
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "description": "At most one of `create`, `cancel` is set (oneof `request`).",
 *   "properties": {
 *     "create": {
 *       "$ref": "#/components/schemas/WatchRequest.Create"
 *     },
 *     "cancel": {
 *       "$ref": "#/components/schemas/WatchRequest.Cancel"
 *     }
 *   }
 * }
 * ```
 */
export interface WatchRequest {
    create: WatchRequestCreate;
    cancel: WatchRequestCancel;
}

/**
 * - key: WatchRequest.Cancel
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "watchId": {
 *       "type": "string",
 *       "format": "int64"
 *     }
 *   }
 * }
 * ```
 */
export interface WatchRequestCancel {
    /** - format: int64 */
    watchId: string;
}

/**
 * - key: WatchRequest.Create
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "watchId": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "assigned by the client"
 *     },
 *     "namespace": {
 *       "type": "string"
 *     },
 *     "kind": {
 *       "$ref": "#/components/schemas/Kind"
 *     },
 *     "name": {
 *       "type": "string",
 *       "description": "empty = every object of the kind"
 *     },
 *     "service": {
 *       "type": "string",
 *       "description": "Instance filter by service"
 *     },
 *     "fromRevision": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "0 = send the current snapshot first, then the increments"
 *     },
 *     "fromEpoch": {
 *       "type": "string",
 *       "description": "required when from_revision > 0; a different epoch gets gone (§7.3)"
 *     }
 *   }
 * }
 * ```
 */
export interface WatchRequestCreate {
    /**
     * assigned by the client
     * - format: int64
     */
    watchId: string;
    namespace: string;
    kind: Kind;
    /** empty = every object of the kind */
    name: string;
    /** Instance filter by service */
    service: string;
    /**
     * 0 = send the current snapshot first, then the increments
     * - format: int64
     */
    fromRevision: string;
    /** required when from_revision > 0; a different epoch gets gone (§7.3) */
    fromEpoch: string;
}

/**
 * At most one of `snapshot`, `events`, `progress`, `gone`, `canceled` is set (oneof `payload`).
 * - key: WatchResponse
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "description": "At most one of `snapshot`, `events`, `progress`, `gone`, `canceled` is set (oneof `payload`).",
 *   "properties": {
 *     "watchId": {
 *       "type": "string",
 *       "format": "int64"
 *     },
 *     "snapshot": {
 *       "$ref": "#/components/schemas/WatchResponse.Snapshot",
 *       "description": "initial state when from_revision = 0"
 *     },
 *     "events": {
 *       "$ref": "#/components/schemas/WatchResponse.Events",
 *       "description": "events of one revision, in one or more messages"
 *     },
 *     "progress": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "bookmark: the revision caught up to, sent periodically while idle"
 *     },
 *     "gone": {
 *       "type": "string",
 *       "format": "int64",
 *       "description": "(from_epoch, from_revision) cannot be resumed; the earliest resumable revision, the client must List again."
 *     },
 *     "canceled": {
 *       "$ref": "#/components/schemas/WatchResponse.Canceled",
 *       "description": "the server ended this watch; the stream and its other watches go on"
 *     }
 *   }
 * }
 * ```
 */
export interface WatchResponse {
    /** - format: int64 */
    watchId: string;
    /** initial state when from_revision = 0 */
    snapshot: WatchResponseSnapshot;
    /** events of one revision, in one or more messages */
    events: WatchResponseEvents;
    /**
     * bookmark: the revision caught up to, sent periodically while idle
     * - format: int64
     */
    progress: string;
    /**
     * (from_epoch, from_revision) cannot be resumed; the earliest resumable revision, the client must List again.
     * - format: int64
     */
    gone: string;
    /** the server ended this watch; the stream and its other watches go on */
    canceled: WatchResponseCanceled;
}

/**
 * Ends one watch without closing the stream: the Create was rejected (e.g. WATCH_QUOTA_EXCEEDED), the namespace was
 *  deleted (NAMESPACE_NOT_FOUND) or the subject lost permission (PERMISSION_DENIED), see §7.2 and §7.3.
 * - key: WatchResponse.Canceled
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "description": "Ends one watch without closing the stream: the Create was rejected (e.g. WATCH_QUOTA_EXCEEDED), the namespace was\n deleted (NAMESPACE_NOT_FOUND) or the subject lost permission (PERMISSION_DENIED), see §7.2 and §7.3.",
 *   "properties": {
 *     "message": {
 *       "type": "string"
 *     },
 *     "detail": {
 *       "$ref": "#/components/schemas/ErrorDetail"
 *     }
 *   }
 * }
 * ```
 */
export interface WatchResponseCanceled {
    message: string;
    detail: ErrorDetail;
}

/**
 * - key: WatchResponse.Event
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "type": {
 *       "$ref": "#/components/schemas/WatchResponse.Event.Type"
 *     },
 *     "resource": {
 *       "$ref": "#/components/schemas/Resource",
 *       "description": "for DELETE: the spec before deletion; revision, updated_at, updated_by of the delete"
 *     }
 *   }
 * }
 * ```
 */
export interface WatchResponseEvent {
    type: WatchResponseEventType;
    /** for DELETE: the spec before deletion; revision, updated_at, updated_by of the delete */
    resource: Resource;
}

/**
 * - `TYPE_UNSPECIFIED`
 * - `TYPE_PUT`
 * - `TYPE_DELETE`
 * - key: WatchResponse.Event.Type
 * - schema: 
 * ```json
 * {
 *   "type": "string",
 *   "description": "- `TYPE_UNSPECIFIED`\n- `TYPE_PUT`\n- `TYPE_DELETE`",
 *   "enum": [
 *     "TYPE_UNSPECIFIED",
 *     "TYPE_PUT",
 *     "TYPE_DELETE"
 *   ]
 * }
 * ```
 */
export enum WatchResponseEventType {
    TYPE_UNSPECIFIED = `TYPE_UNSPECIFIED`,
    TYPE_PUT = `TYPE_PUT`,
    TYPE_DELETE = `TYPE_DELETE`
}

/**
 * - key: WatchResponse.Events
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "revision": {
 *       "type": "string",
 *       "format": "int64"
 *     },
 *     "events": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/WatchResponse.Event"
 *       }
 *     },
 *     "more": {
 *       "type": "boolean",
 *       "description": "more events of this revision follow; apply them together once the last arrives (§4.6)"
 *     }
 *   }
 * }
 * ```
 */
export interface WatchResponseEvents {
    /** - format: int64 */
    revision: string;
    events: WatchResponseEvent[];
    /** more events of this revision follow; apply them together once the last arrives (§4.6) */
    more: boolean;
}

/**
 * - key: WatchResponse.Snapshot
 * - schema: 
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "items": {
 *       "type": "array",
 *       "items": {
 *         "$ref": "#/components/schemas/Resource"
 *       }
 *     },
 *     "revision": {
 *       "type": "string",
 *       "format": "int64"
 *     },
 *     "epoch": {
 *       "type": "string",
 *       "description": "the timeline of revision; revisions compare only within one epoch (§6.4)"
 *     },
 *     "more": {
 *       "type": "boolean",
 *       "description": "more chunks of this snapshot follow; no events arrive before the last one (§4.6)"
 *     }
 *   }
 * }
 * ```
 */
export interface WatchResponseSnapshot {
    items: Resource[];
    /** - format: int64 */
    revision: string;
    /** the timeline of revision; revisions compare only within one epoch (§6.4) */
    epoch: string;
    /** more chunks of this snapshot follow; no events arrive before the last one (§4.6) */
    more: boolean;
}
