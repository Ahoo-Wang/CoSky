export class RowExpand<Data> {
  data: Data;
  expand = false;

  constructor(data: Data, expand: boolean) {
    this.data = data;
    this.expand = expand;
  }

  static of<Data>(data: Data, expand: boolean = false): RowExpand<Data> {
    return new RowExpand(data, expand);
  }
}
