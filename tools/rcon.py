#!/usr/bin/env python3
"""Sends commands to the dev server over RCON and prints what it says back.

    python3 tools/rcon.py "setblock 0 -60 0 electricity:electric_cabin[facing=north]"
    python3 tools/rcon.py -f commands.txt

Why this exists: gradle's runServer does not forward stdin, so there is no console to type into, and a
mod whose behaviour is placement and breaking cannot be tested by reading it. RCON is already enabled in
run/server.properties, so this is the whole of what was missing - forty lines of a very simple protocol.

The password and port are read from run/server.properties rather than passed in, so this cannot drift
from the server it is talking to.
"""

import os
import socket
import struct
import sys

PROPERTIES = os.path.join('run', 'server.properties')

TYPE_AUTH = 3
TYPE_COMMAND = 2


def settings():
    values = {}
    for line in open(PROPERTIES):
        if '=' in line and not line.startswith('#'):
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip()

    return values.get('rcon.password', ''), int(values.get('rcon.port', '25575'))


def packet(request_id, kind, body):
    payload = struct.pack('<ii', request_id, kind) + body.encode('utf-8') + b'\x00\x00'
    return struct.pack('<i', len(payload)) + payload


def read(sock):
    header = sock.recv(4)
    if len(header) < 4:
        return None, ''

    length = struct.unpack('<i', header)[0]
    data = b''
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            break
        data += chunk

    request_id, _ = struct.unpack('<ii', data[:8])
    return request_id, data[8:-2].decode('utf-8', 'replace')


def run(commands):
    password, port = settings()
    with socket.create_connection(('127.0.0.1', port), timeout=10) as sock:
        sock.sendall(packet(1, TYPE_AUTH, password))
        request_id, _ = read(sock)
        if request_id == -1:
            raise SystemExit('rcon: the server refused the password in %s' % PROPERTIES)

        for index, command in enumerate(commands):
            sock.sendall(packet(2 + index, TYPE_COMMAND, command))
            _, reply = read(sock)
            print('> %s\n%s' % (command, reply.strip() or '(no output)'))


def main():
    if len(sys.argv) >= 3 and sys.argv[1] == '-f':
        commands = [line.strip() for line in open(sys.argv[2]) if line.strip() and not line.startswith('#')]
    else:
        commands = sys.argv[1:]

    if not commands:
        raise SystemExit(__doc__)

    run(commands)


if __name__ == '__main__':
    main()
