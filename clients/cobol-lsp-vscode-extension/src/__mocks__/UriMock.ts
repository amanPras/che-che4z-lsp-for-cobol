export class Uri {
  constructor(
    public path: string,
    public scheme: string = "file",
  ) {}
  static parse(str: string): Uri {
    // Parsing regex from original VSCode Uri implementation
    // https://github.com/microsoft/vscode-uri/blob/edfdccd976efaf4bb8fdeca87e97c47257721729/src/uri.ts#L79
    const _regexp =
      /^(([^:/?#]+?):)?(\/\/([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?/;
    const match = _regexp.exec(str);
    if (match) {
      return new Uri(match[5], match[2]);
    }
    return new Uri(str);
  }

  static file(str: string): Uri {
    return new Uri(str);
  }

  static joinPath(base: Uri, ...pathSegments: string[]): Uri {
    const segment = pathSegments.shift();
    if (segment === "../.bridge.json") {
      const uri = new Uri(
        base.path.substring(0, base.path.lastIndexOf("/")) + "/.bridge.json",
      );
      return pathSegments.length > 0
        ? this.joinPath(uri, ...pathSegments)
        : uri;
    }
    if (segment == "..") {
      const uri = new Uri(base.path.substring(0, base.path.lastIndexOf("/")));
      return pathSegments.length > 0
        ? this.joinPath(uri, ...pathSegments)
        : uri;
    }
    const uri = new Uri(base.path + "/" + segment);
    return pathSegments.length > 0 ? this.joinPath(uri, ...pathSegments) : uri;
  }

  static from(components: {
    scheme: string;
    authority?: string;
    path: string;
    query?: string;
    fragment?: string;
  }): Uri {
    return new Uri(components.path, components.scheme);
  }

  get fsPath(): string {
    let value = this.path;
    if (this.path[2] === ":") {
      // substring to remove the first /
      value = value.substring(1).replace(/\//g, "\\");
    }
    return value;
  }

  toString(): string {
    return `${this.scheme}:${this.path}`;
  }
}
