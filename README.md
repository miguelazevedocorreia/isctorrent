# IscTorrent

P2P file sharing system developed in Java.

## Execution

```bash
java pt.iscte.pcd.isctorrent.Main <port> <working_directory>
```

Example:
```bash
java pt.iscte.pcd.isctorrent.Main 8081 dl1
```

## Features

- Peer-to-peer architecture without central server
- Graphical interface for search and downloads
- Distributed download in blocks from multiple peers
- Coordination using custom synchronization mechanisms

## How to Use

1. **Connect to peers**: Use the "Connect" button to link with other peers
2. **Search files**: Enter a keyword and click "Search"
3. **Download**: Select files from the list and click "Download"

## Project Structure

- `core/` - Core system classes
- `download/` - Distributed download management
- `network/` - Peer-to-peer communication
- `gui/` - Graphical interface
- `protocol/` - Communication messages
- `sync/` - Custom synchronization

## Requirements

- Java 17 or higher
- Only standard Java libraries
