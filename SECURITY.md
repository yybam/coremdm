# Security Policy

## Supported Versions

CORE MDM is in beta. Only the latest release receives fixes.

| Version | Supported |
| ------- | --------- |
| Latest release ([v0.4.2](https://github.com/yybam/coremdm/releases/latest)) | :white_check_mark: |
| Older releases | :x: |

## Reporting a Vulnerability

Please report security issues privately by [opening a GitHub security advisory](https://github.com/yybam/coremdm/security/advisories/new),
or a regular [issue](https://github.com/yybam/coremdm/issues/new) if the problem is not sensitive.

Because CORE MDM can lock, wipe, and apply device-owner policies to enrolled phones, please take
particular care with reports involving:

- the Firestore security rules (`firestore.rules`) — cross-tenant device access or command injection
- enrollment-token handling and device claiming
- anything that lets a non-owner read or command another user's device

Please do not include working exploit code in a public issue. We aim to acknowledge reports
within a few days.
