# NullSec WiFi Documentation

## Overview

NullSec WiFi is a comprehensive WiFi security analysis and auditing toolkit. It provides tools for wireless network assessment, WPA/WPA2/WPA3 testing, rogue AP detection, and wireless IDS capabilities.

## Tools

| Tool | Purpose |
|------|---------|
| `wifi-scan` | Comprehensive wireless scanning |
| `wifi-audit` | Security assessment of target networks |
| `wifi-capture` | Handshake and PMKID capture |
| `wifi-crack` | Hashcat/Aircrack-ng integration |
| `wifi-rogue` | Rogue AP detection |
| `wifi-ids` | Wireless intrusion detection |
| `wifi-deauth` | Deauthentication testing |
| `wifi-jam` | Channel analysis and interference |

## Quick Start

```bash
# Scan for networks
sudo nullsec-wifi scan wlan0

# Full security audit
sudo nullsec-wifi audit --target "TargetSSID" --interface wlan0mon

# Capture WPA handshake
sudo nullsec-wifi capture --bssid AA:BB:CC:DD:EE:FF --channel 6

# Start wireless IDS
sudo nullsec-wifi ids --interface wlan0mon --alert webhook
```

## Hardware Support

Works with any monitor-mode capable wireless adapter. Tested with:
- Alfa AWUS036ACH/ACM
- TP-Link TL-WN722N (v1)
- Panda PAU09
- WiFi Pineapple Mark VII

## Integration

NullSec WiFi integrates with the broader NullSec ecosystem — results feed into NullSec Linux dashboards and can be piped to other tools via `nullsec-pipe`.
