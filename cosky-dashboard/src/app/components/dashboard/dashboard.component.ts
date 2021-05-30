import {Component, OnInit} from '@angular/core';
import {StatClient} from '../../api/stat/StatClient';
import {NamespaceContext} from '../../core/NamespaceContext';
import {StatDto} from '../../api/stat/StatDto';
import {Stats} from '../../api/stat/Stats';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  stat: StatDto;

  constructor(private namespaceContext: NamespaceContext,
              private statClient: StatClient) {
    this.stat = Stats.of();
  }

  ngOnInit(): void {
    this.getStat();
    this.namespaceContext.subscribeNamespaceChanged('/dashboard', ns => {
      this.getStat();
    });
  }

  private getStat(): void {
    this.statClient.getStat(this.namespaceContext.ensureCurrentNamespace()).subscribe(stat => {
      this.stat = stat;
    });
  }
}
