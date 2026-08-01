export class ObjectUrlRegistry {
  private readonly urls = new Set<string>();

  create(blob: Blob): string {
    const url = URL.createObjectURL(blob);
    this.urls.add(url);
    return url;
  }

  revoke(url: string | undefined): void {
    if (!url) {
      return;
    }

    this.urls.delete(url);
    URL.revokeObjectURL(url);
  }

  revokeAll(): void {
    this.urls.forEach((url) => URL.revokeObjectURL(url));
    this.urls.clear();
  }
}
