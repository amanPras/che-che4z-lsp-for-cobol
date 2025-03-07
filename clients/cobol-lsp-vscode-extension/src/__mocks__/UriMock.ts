export class Uri {
  constructor(
    public path: string,
    public scheme: string = "file",
  ) {}
  static parse(str: string): Uri {
    const splitPath = str.split("://");
    const scheme = splitPath.length > 1 ? splitPath[0] : "file";
    const path = splitPath.length > 1 ? splitPath[1] : str;
    return new Uri(path, scheme);
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
    return new Uri(components.path);
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
    return "file://" + this.path;
  }
}
