interface UploadFile {
  file: File;
  progress: number; // 0..100
  status: 'idle' | 'uploading' | 'done' | 'error';
}
