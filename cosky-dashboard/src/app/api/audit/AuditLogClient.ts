import {Injectable} from "@angular/core";
import {environment} from "../../../environments/environment";
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {QueryLogResponse} from "./QueryLogResponse";

@Injectable({providedIn: 'root'})
export class AuditLogClient {
  apiPrefix = environment.coskyRestApiHost + '/audit-log';

  constructor(private httpClient: HttpClient) {

  }

  queryLog(offset: number, limit: number): Observable<QueryLogResponse> {
    const apiUrl = `${this.apiPrefix}?offset=${offset}&limit=${limit}`;
    return this.httpClient.get<QueryLogResponse>(apiUrl);
  }
}
