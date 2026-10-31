package com.bitpoint.homeservercontrol;

/** Explicit peer metadata: never infer a remote device from its IP or screen size. */
enum DeviceKind {
    PHONE("phone","Телефон","телефон","телефона"),
    TABLET("tablet","Планшет","планшет","планшета"),
    PC("pc","ПК","ПК","ПК"),
    UNKNOWN("unknown","Сервер","сервер","сервера");
    final String wire,label,target,folder;
    DeviceKind(String wire,String label,String target,String folder){this.wire=wire;this.label=label;this.target=target;this.folder=folder;}
    static DeviceKind decode(String wire){
        for(DeviceKind kind:values())if(kind.wire.equals(wire))return kind;
        throw new IllegalArgumentException("Invalid device kind");
    }
}
