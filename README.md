# ItemsAdderUploadPlus (IAUP)

Automatically ship your resourcepack to S3 (and more in the future). This enabled professional storage & serve to client more efficiently!

## Setup

### 1. Configure ItemsAdder To Use `external-host`
In `plugins/ItemsAdder/config.yml`, ensure hosting is set like this:
```yml
resource-pack:
  no-host:
    enabled: false
  lobfile:
    enabled: false
  self-host:
    enabled: false
  external-host:
    enabled: true
```

IAUP will automatically configure `resource-pack.hosting.external-host.url`

### 3) Configure IAUP (`plugins/ItemsAdderUploadPlus/config.yml`)
Minimum required keys:
1. `s3.bucket`
2. `s3.access_key`
3. `s3.secret_key`

Common options:
1. `s3.endpoint`
   AWS: `https://s3.amazonaws.com`
   MinIO: `http(s)://<host>:<port>`
   Cloudflare R2: `https://<accountid>.r2.cloudflarestorage.com`
2. `s3.region`
   AWS: real region (example `us-east-1`)
   R2: `auto`
3. `s3.path_scheme`
   Supports placeholders: `{uid} {file} {name} {ext} {date} {ts}`
4. `s3.public_url_base`
   Use when the bucket is publicly readable via a known domain (CDN/custom domain).
   URL is built as `<public_url_base>/<path_scheme-resolved-objectKey>`.
   IAUP does not auto-prepend `s3.bucket` when this is set and `s3.use_presigned_url: false`.
5. `s3.use_presigned_url`
   Use when you do not want a public bucket. The URL will expire (`s3.presigned_expiry_seconds`).

Auto reload after manual upload:
- `global.auto_iareload_after_manual_upload: true`
  When enabled, a successful `/iaup upload` triggers `/iareload`.
- `global.auto_iareload_after_auto_upload: true`
  When enabled, successful auto-upload from `iazip` flow also triggers `/iareload`.

### 4) Permissions / Policies

If you use `s3.use_presigned_url: false`, clients must be able to download the pack URL.
Either is needed:

- Gateway is attached to a CDN-enabled domain (recommended, cache compatible)
- S3 endpoint is public-readable

If you use `s3.use_presigned_url: true`, the pack URL is a signed URL and can work with private buckets. However, this may lead to client redownload cached pack...

Path-style endpoint note:
- Bucket prefix in generated URL (`/<bucket>/<object>`) is only applied on endpoint path-style branch.
- If `s3.public_url_base` is set, generated URL uses that base directly with object key path.

#### Example: Bucket policy for public reads (AWS/MinIO/RustFS)
Replace `<bucket-name>` with your bucket name.
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "AWS": "*" },
      "Action": [
        "s3:GetObjectTagging",
        "s3:GetBucketLocation",
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::<bucket-name>/*"
      ]
    },
    {
      "Effect": "Deny",
      "Principal": { "AWS": "arn:aws:iam::*:root" },
      "Action": [
        "s3:DeleteObject",
        "s3:PutObjectTagging",
        "s3:DeleteObjectTagging",
        "s3:PutObject"
      ],
      "Resource": [
        "arn:aws:s3:::<bucket-name>/*"
      ]
    }
  ]
}
```

#### Example: Credentials policy for the uploader key (access key/secret key)
Replace `<bucket-name>` with your bucket name.
```json
{
  "ID": "",
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "",
      "Effect": "Allow",
      "Action": "s3:*",
      "NotAction": [],
      "Resource": [
        "arn:aws:s3:::<bucket-name>",
        "arn:aws:s3:::<bucket-name>/*"
      ],
      "NotResource": [],
      "Condition": {}
    }
  ]
}
```

### 5) Generate And Upload
1. Generate the pack with ItemsAdder:
   Run `/iazip` (or any workflow that produces `plugins/ItemsAdder/output/generated.zip`).
2. Upload:
   Run `/iaup upload`
3. If you did not enable `global.auto_iareload_after_manual_upload`, run:
   `/iareload`

## Commands
- `/iaup upload` uploads the pack immediately
- `/iaup reload` reloads IAUP config and rebinds hooks

## CI/CD Release
- `Build` workflow runs on push/PR to `main`.
- `Release` workflow is separate and supports:
1. Comment command: `/release`, `/release patch`, `/release minor`, `/release major`
2. Manual dispatch: run `Release` workflow and choose bump type

Release workflow does:
1. Bumps `version` in `gradle.properties` (semver)
2. Builds artifacts
3. Commits version bump, tags `vX.Y.Z`, pushes to `main`
4. Creates GitHub Release with JAR assets

Optional Modrinth publish during release:
- Required secrets:
1. `MODRINTH_TOKEN`
2. `MODRINTH_PROJECT_ID`
- Optional repository variables:
1. `MODRINTH_GAME_VERSIONS` (JSON array, default `["1.20.1"]`)
2. `MODRINTH_LOADERS` (JSON array, default `["paper","spigot","purpur"]`)
