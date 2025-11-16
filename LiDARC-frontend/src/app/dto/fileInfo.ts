export class FileInfo {
  constructor(public fileName: string, public method: HttpMethod, public presignedURL?: string) {}
}

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS';
