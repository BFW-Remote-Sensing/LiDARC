export class FileInfo {
    constructor(
        public fileName: string,
        public presignedUrl: string,
        public method: HttpMethod
    ) {}
}

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS';