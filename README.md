# Server

Multi-server awareness. Commands to transport players to certain
servers.

## Commands

- `/server` Global server switch command for users
- `/<servername>` One command for every connected server

## Admin commands

- `/serveradm reload` Reload the `tag.json` file
- `/serveradm refresh` Refresh all servers via Redis
- `/serveradm list` List all servers
- `/serveradm info <server>` Dump server info (ServerTag)
- `/serveradm set <key> <value>` Update ServerTag. This will broadcast and save it

## Permissions

- `server.server` Use `/server`
- `server.locked` Visit locked servers
- `server.hidden` See hidden servers
- `server.admin` Use /serveradmin

## Server Tag

Server tags are stored in Redis and also broadcast to all servers on
every update. The owning server will attempt to read it from its data
folder (tag.json). It can be modified with the admin command, which
will save and broadcast it.

## Redis keys

- `cavetale.server.<name>` A JSON representation of a ServerTag object
- `cavetale.server_choice.<uuid>` (60s) When a player tried to switch to a server while it was down
- `cavetale.server_switch.<uuid>` (10s) Set before, a player was sent to another server, read and deleted right after
- `cavetale.server_wake.<server>` Message queue to wake up a sleeping server (lpush, brpop)

## Server Wait

When a server is not online when a player attempts to go there, they
are notified that it is currently starting and moved once it comes
up. If the server is mared as `waitOnWake`, it will be sent the
`wake_up` signal via the `server_wake` message queue.

The `WakeOnWait` folder in the root directory indicates to the
executing script that this server should wait to start up until it
receives the `wake_up` signal. This plugin will look for the file and
if it is found, update the corresopnding field in the server
tag. Conversely, when the field is set via command, the file is
created, containing the name of this server because the script needs
to know.