#!/usr/bin/python3 -I
"""Root-owned RPM helper. Fixed operations only; authorization is performed by pkexec."""
import json, os, pathlib, subprocess, sys

PLANS = {
 'install': [('/usr/bin/dnf', '-y', 'install', 'openssh-server')],
 'start': [('/usr/bin/systemctl', 'start', 'sshd.service')],
 'stop': [('/usr/bin/systemctl', 'stop', 'sshd.service')],
 'restart': [('/usr/bin/systemctl', 'restart', 'sshd.service')],
 'enable': [('/usr/bin/systemctl', 'enable', 'sshd.service')],
 'disable': [('/usr/bin/systemctl', 'disable', 'sshd.service')],
 'firewall-open': [('/usr/bin/firewall-cmd', '--add-service=ssh'), ('/usr/bin/firewall-cmd', '--permanent', '--add-service=ssh')],
 'firewall-close': [('/usr/bin/firewall-cmd', '--remove-service=ssh'), ('/usr/bin/firewall-cmd', '--permanent', '--remove-service=ssh')],
 'tailscale-up': [('/usr/bin/tailscale', 'up', '--timeout=30s')],
 'tailscale-down': [('/usr/bin/tailscale', 'down')],
}

def plan(args):
 if len(args)!=1 or args[0] not in PLANS: raise ValueError('INVALID_ACTION')
 return PLANS[args[0]]

def main(args):
 try:
  commands=plan(args)
  if os.geteuid()!=0: raise ValueError('ADMIN_REQUIRED')
  if not any(line=='ID=fedora' for line in pathlib.Path('/etc/os-release').read_text().splitlines()):
   raise ValueError('FEDORA_REQUIRED')
  for command in commands:
   executable=pathlib.Path(command[0]); st=executable.stat()
   if st.st_uid!=0 or st.st_mode & 0o022: raise ValueError('UNTRUSTED_PROGRAM')
   result=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL,timeout=600)
   if result.returncode: raise ValueError('SYSTEM_COMMAND_FAILED')
  print(json.dumps({'ok':True,'action':args[0]})); return 0
 except (ValueError,OSError,subprocess.TimeoutExpired) as error:
  code=str(error) if isinstance(error,ValueError) else 'SYSTEM_COMMAND_FAILED'
  print(json.dumps({'ok':False,'code':code})); return 1

if __name__=='__main__': sys.exit(main(sys.argv[1:]))
