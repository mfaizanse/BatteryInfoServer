# Release Process

Releases are created via the manual **Release** GitHub Actions workflow. The workflow validates the version, builds a signed APK, and publishes a GitHub Release with the APK attached.

## Prerequisites

The four secrets below must be configured in the repository before the workflow can run. They only need to be set up once.

---

## Step 1 — Create the signing keystore

The keystore is the file that cryptographically signs the APK. It must be kept safe — losing it means you can no longer publish updates under the same signing identity.

Run this command on your local machine (requires JDK installed):

```bash
keytool -genkeypair \
  -keystore batteryinfoserver.jks \
  -alias batteryinfoserver \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storetype PKCS12
```

`keytool` will prompt for:
- **Keystore password** — choose a strong password, save it securely
- **Key password** — can be the same as the keystore password, save it securely
- **Distinguished name fields** — first/last name, organisation, city, state, country (informational only for a personal app)

Store `batteryinfoserver.jks` in a safe place (e.g. a password manager or encrypted backup). Do not commit it to the repository.

---

## Step 2 — Configure GitHub Secrets

Go to **GitHub → Settings → Secrets and variables → Actions → New repository secret** and add the following four secrets:

### `KEYSTORE_BASE64`

The keystore file encoded as base64. Run:

```bash
base64 -i batteryinfoserver.jks | pbcopy   # macOS — copies to clipboard
```

Paste the clipboard contents as the secret value.

### `KEY_STORE_PASSWORD`

The keystore password you chose when running `keytool -genkeypair`.

### `KEY_ALIAS`

The alias you passed to `-alias` when creating the keystore. In the example above this is `batteryinfoserver`.

### `KEY_PASSWORD`

The key password you chose when running `keytool -genkeypair`.

---

## Step 3 — Run the release workflow

1. Go to **GitHub → Actions → Release**
2. Click **Run workflow**
3. Enter the version in `major.minor.patch` format (e.g. `1.0.0`)
4. Click **Run workflow**

### What the workflow does

| Step | Description |
|---|---|
| Validate version | Rejects input that is not `major.minor.patch` (e.g. `1.0` fails) |
| Check for duplicates | Fails if the git tag or GitHub Release already exists |
| Build signed APK | Decodes the keystore secret and passes signing credentials to Gradle at runtime — no credentials are stored in source code |
| Rename APK | Produces `batteryinfoserver_1.0.0.apk` (version-stamped) |
| Create GitHub Release | Creates a release tagged `v1.0.0` with auto-generated notes and the APK attached |

### Expected output

- A git tag `v1.0.0` is created
- A GitHub Release named `v1.0.0` appears on the Releases page
- The release has `batteryinfoserver_1.0.0.apk` attached

---

## Troubleshooting

**"Invalid version format"** — the version input must be exactly `major.minor.patch`, e.g. `1.0.0`. Inputs like `1.0` or `v1.0.0` are rejected.

**"Tag/Release already exists"** — that version has already been released. Increment the version number.

**"Keystore was tampered with, or password was incorrect"** — the `KEY_STORE_PASSWORD` or `KEY_PASSWORD` secret does not match the keystore. Re-check the values in GitHub Secrets.

**APK not signed / zipalign error** — ensure all four secrets (`KEYSTORE_BASE64`, `KEY_STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) are set and non-empty.
