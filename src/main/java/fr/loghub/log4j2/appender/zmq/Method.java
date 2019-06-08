package fr.loghub.log4j2.appender.zmq;

import org.zeromq.ZMQ;

enum Method {
    CONNECT {
        @Override
        public void act(ZMQ.Socket socket, String address) { socket.connect(address); }

        @Override
        public char getSymbol() {
            return '-';
        }
    },
    BIND {
        @Override
        public void act(ZMQ.Socket socket, String address) { socket.bind(address); }

        @Override
        public char getSymbol() {
            return 'O';
        }
    };
    public abstract void act(ZMQ.Socket socket, String address);
    public abstract char getSymbol();
}
