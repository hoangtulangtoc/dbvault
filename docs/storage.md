# Storing backups in S3-compatible object storage

Backups go to local disk unless a target names a **storage profile**. A profile points at a bucket
in any S3-compatible service — AWS S3, MinIO, or another provider that speaks the S3 API — and a
target that uses it keeps its artifacts there instead of on the dbvault host.

## Create a profile

Open **Storage → Add S3 profile** and fill in:

| Field | What to enter |
| --- | --- |
| Name | How the profile appears when you pick it for a target. Unique, ignoring case. |
| Endpoint | Leave empty for AWS S3. For MinIO or another provider, its base URL, for example `https://minio.internal:9000`. |
| Region | The bucket's region. MinIO accepts any value; `us-east-1` is the convention. |
| Bucket | An existing bucket. dbvault never creates one. |
| Key prefix | Optional. Every object of this profile is written below it, so one bucket can serve several installations. |
| Path-style access | Needed by most self-hosted services. Ticked automatically when you enter an endpoint. |
| Credentials | **Static**: an access key ID and secret, stored encrypted. **Default chain**: whatever AWS's default provider chain finds — environment, instance profile, or web identity. |

Use **Test** on the profile list before relying on a profile: it writes a small probe object, reads it back,
compares the bytes and deletes it, so it proves every permission a backup and a restore need.

## What dbvault needs from the bucket

The credentials need to list, read, write and delete objects below the profile's prefix. For a
dedicated MinIO user, a minimal policy is:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": ["s3:ListBucket"], "Resource": ["arn:aws:s3:::dbvault-backups"] },
    { "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:AbortMultipartUpload"],
      "Resource": ["arn:aws:s3:::dbvault-backups/*"] }
  ]
}
```

## How backups reach the bucket

A backup is first written to a private directory below `STORAGE_STAGING_DIR`, where its size and
SHA-256 are computed; only then is it uploaded, in parts, and only after the upload completes is the
backup marked successful. Objects are named `<prefix>/<target-id>/<execution-id>/<file>`, so two
backups never share a key.

A restore downloads the artifact to staging, checks its SHA-256 against the one recorded when it was
written, and only then hands it to the database client. Staging is cleaned up whether the restore
succeeds or fails. Give `STORAGE_STAGING_DIR` room for the largest single backup you keep.

## Changing a profile later

A target's profile can be changed at any time; only backups taken afterwards use the new one.
Once any backup refers to a profile, its endpoint, region, bucket, prefix and path-style setting are
fixed, because existing artifacts are found through them. You can still rename the profile and rotate
its credentials. A profile cannot be deleted while a target or a stored backup still uses it.

See [ADR-025](adr/025-s3-storage-profiles-and-local-staging.md) for why storage works this way.
