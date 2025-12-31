// file: LiDARC-frontend/src/app/service/uploadQueue.service.ts
import {Injectable} from '@angular/core';
import {BehaviorSubject, Subscription} from 'rxjs';
import {UploadFile} from '../entity/UploadFile';

@Injectable({providedIn: 'root'})
export class UploadQueueService {
  private filesSubject = new BehaviorSubject<UploadFile[]>([]);
  files$ = this.filesSubject.asObservable();

  private idToSubscriptionMap = new Map<string, Subscription>();

  get files(): UploadFile[] {
    return this.filesSubject.value;
  }

  addFiles(files: File[], folderId?: number, folderName?: string) {
    const newUploads: UploadFile[] = files.map(f => ({
      id: this.makeId(),
      file: f,
      hash: '',
      status: 'idle',
      progress: 0,
      folderId,
      folderName
    }));

    this.filesSubject.next([...this.files, ...newUploads]);
    return newUploads;
  }

  updateFile(id: string, updates: Partial<UploadFile>) {
    const updated = this.files.map(f => (f.id === id ? {...f, ...updates} : f));
    this.filesSubject.next(updated);
  }

  removeFile(id: string) {
    const sub = this.idToSubscriptionMap.get(id);
    sub?.unsubscribe();
    this.idToSubscriptionMap.delete(id);
    this.filesSubject.next(this.files.filter(f => f.id !== id));
  }

  clear() {
    this.idToSubscriptionMap.forEach(sub => sub.unsubscribe());
    this.idToSubscriptionMap.clear();
    this.filesSubject.next([]);
  }

  setSubscription(id: string, subscription: Subscription) {
    this.idToSubscriptionMap.set(id, subscription);
  }

  cancelUpload(id: string) {
    const sub = this.idToSubscriptionMap.get(id);
    if (sub) {
      sub.unsubscribe();
      this.idToSubscriptionMap.delete(id);
      this.updateFile(id, {status: 'error', progress: 0});
    }
  }

  private makeId() {
    return crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;
  }
}

