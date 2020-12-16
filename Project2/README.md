# sdis1920-t5g22

## Compile
```javac *.java```

## Run Peer
```java Peer <address> <port> [<address_of_peer_in_system> <port_of_peer_in_system>]```

## Run TestApp
### Backup
```java TestApp <address> <port> BACKUP <file> <replication_degree>```
### Restore
```java TestApp <address> <port> RESTORE <file>```
### Delete
```java TestApp <address> <port> DELETE <file>```
### Reclaim
```java TestApp <address> <port> RECLAIM <max_space>```