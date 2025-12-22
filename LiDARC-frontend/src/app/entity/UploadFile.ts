interface UploadFile {
  file: File;
  hash: string;
  progress: number; // 0..100
  status: 'idle' | 'uploading' | 'done' | 'error';
  folderId?: number;
  folderName?: string;
}
