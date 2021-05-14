import {Observable} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {environment} from '../../../environments/environment';

@Injectable({providedIn: 'root'})
export class NamespaceClient {
  apiPrefix = environment.governRestApiHost + '/namespaces';

  constructor(private httpClient: HttpClient) {

  }

  getNamespaces(): Observable<string[]> {
    return this.httpClient.get<string[]>(this.apiPrefix);
  }

  getCurrent(): Observable<string> {
    const apiUrl = this.apiPrefix + '/current';
    return this.httpClient.get<string>(apiUrl);
  }

  setCurrentContextNamespace(namespace: string): Observable<object> {
    const apiUrl = `${this.apiPrefix}/current/${namespace}`;
    return this.httpClient.put<object>(apiUrl, null);
  }


  setNamespace(namespace: string): Observable<boolean> {
    const apiUrl = `${this.apiPrefix}/${namespace}`;
    return this.httpClient.put<boolean>(apiUrl, null);
  }

  removeNamespace(namespace: string): Observable<boolean> {
    const apiUrl = `${this.apiPrefix}/${namespace}`;
    return this.httpClient.delete<boolean>(apiUrl);
  }


}
