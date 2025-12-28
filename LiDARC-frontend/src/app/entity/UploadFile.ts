// UploadFile.ts
export interface UploadFile {
  id: string;
  file: File;
  hash: string;
  status: 'idle' | 'uploading' | 'done' | 'error';
  progress: number;
  folderId?: number;
  folderName?: string;
}
