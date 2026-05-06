ALTER TABLE storage.blob_objects ADD COLUMN folder TEXT;
CREATE INDEX idx_blob_objects_folder ON storage.blob_objects (folder);
