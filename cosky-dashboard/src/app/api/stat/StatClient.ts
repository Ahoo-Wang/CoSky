import {Observable} from 'rxjs';
import {HttpClient} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {environment} from '../../../environments/environment';
import {StatDto} from './StatDto';

@Injectable({providedIn: 'root'})
export class StatClient {
  apiPrefix = environment.coskyRestApiHost + '/namespaces';

  constructor(private httpClient: HttpClient) {

  }
  getStat(namespace: string): Observable<StatDto> {
    const apiUrl = `${this.apiPrefix}/${namespace}/stat`;
    return this.httpClient.get<StatDto>(apiUrl);
  }


}
