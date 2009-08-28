/**
 * junixsocket
 *
 * Copyright (c) 2009 NewsClub, Christian Kohlschütter
 *
 * The author licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.newsclub.net.unix.rmi;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.Map;

import org.newsclub.net.unix.AFUNIXSocket;

/**
 * The {@link AFUNIXSocket}-compatible equivalent of {@link Naming}. Use this
 * class for accessing RMI registries that are reachable by {@link AFUNIXSocket}
 * s.
 * 
 * @author Christian Kohlschuetter
 */
public final class AFUNIXNaming {
    private static final String PORT_ASSIGNER_ID = PortAssigner.class.getName();

    private static final Map<SocketDirAndPort, AFUNIXNaming> instances = new HashMap<SocketDirAndPort, AFUNIXNaming>();

    /**
     * Returns the default instance of {@link AFUNIXNaming}. Sockets are stored
     * in <code>java.io.tmpdir</code>.
     * 
     * @return
     */
    public static AFUNIXNaming getInstance() throws IOException {
        return getInstance(AFUNIXRMISocketFactory.DEFAULT_SOCKET_DIR,
                AFUNIXRMIPorts.DEFAULT_REGISTRY_PORT);
    }

    /**
     * Returns the
     * 
     * @param socketDir
     * @return
     */
    public static AFUNIXNaming getInstance(final File socketDir)
            throws RemoteException {
        return getInstance(socketDir, AFUNIXRMIPorts.DEFAULT_REGISTRY_PORT);
    }

    public static AFUNIXNaming getInstance(final File socketDir,
            final int registryPort) throws RemoteException {
        final SocketDirAndPort sap = new SocketDirAndPort(socketDir,
                registryPort);
        AFUNIXNaming instance;
        synchronized (AFUNIXNaming.class) {
            instance = instances.get(sap);
            if (instance == null) {
                instance = new AFUNIXNaming(sap.socketDir, registryPort);
                instances.put(sap, instance);
            }
        }
        return instance;
    }

    private Registry registry = null;
    private PortAssigner portAssigner = null;
    private final File registrySocketDir;
    private final int registryPort;
    private AFUNIXRMISocketFactory socketFactory;

    public AFUNIXNaming(final File socketDir, final int port) {
        this.registrySocketDir = socketDir;
        this.registryPort = port;
        this.socketFactory = new AFUNIXRMISocketFactory(this, socketDir);
    }

    public AFUNIXRMISocketFactory getSocketFactory() {
        return socketFactory;
    }

    public File getRegistrySocketDir() {
        return registrySocketDir;
    }

    public int getRegistryPort() {
        return registryPort;
    }

    public PortAssigner getPortAssigner() throws RemoteException,
            NotBoundException {
        if (portAssigner != null) {
            return portAssigner;
        }
        portAssigner = getPortAssignerFromRegistry();
        return portAssigner;
    }

    PortAssigner getPortAssignerFromRegistry() throws RemoteException,
            NotBoundException {
        PortAssigner assigner;
        synchronized (PortAssigner.class) {
            try {
                assigner = (PortAssigner) lookup(PORT_ASSIGNER_ID);
            } catch (MalformedURLException e) {
                throw (RemoteException) new RemoteException(e.getMessage())
                        .initCause(e);
            }
            return assigner;
        }
    }

    private void rebindPortAssigner(final PortAssigner assigner)
            throws RemoteException {
        portAssigner = assigner;
        getRegistry().rebind(PORT_ASSIGNER_ID, assigner);
    }

    public Registry getRegistry() throws RemoteException {
        if (registry == null) {
            registry = LocateRegistry.getRegistry(null, registryPort,
                    socketFactory);
        }
        return registry;
    }

    public Remote lookup(String name) throws NotBoundException,
            java.net.MalformedURLException, RemoteException {
        return getRegistry().lookup(name);
    }

    public void unbind(String name) throws RemoteException, NotBoundException,
            java.net.MalformedURLException {
        getRegistry().unbind(name);
    }

    public void bind(String name, Remote obj) throws AlreadyBoundException,
            java.net.MalformedURLException, RemoteException {
        getRegistry().bind(name, obj);
    }

    public void rebind(String name, Remote obj)
            throws java.net.MalformedURLException, RemoteException {
        getRegistry().rebind(name, obj);
    }

    private static final class SocketDirAndPort {
        File socketDir;
        int port;

        public SocketDirAndPort(File socketDir, int port)
                throws RemoteException {
            try {
                this.socketDir = socketDir.getCanonicalFile();
            } catch (IOException e) {
                throw (RemoteException) new RemoteException(e.getMessage())
                        .initCause(e);
            }
            this.port = port;
        }

        public int hashCode() {
            return socketDir == null ? port : socketDir.hashCode() ^ port;
        }

        public boolean equals(Object o) {
            if (o instanceof SocketDirAndPort) {
                SocketDirAndPort other = (SocketDirAndPort) o;
                if (port != other.port) {
                    return false;
                }
                if (socketDir == null) {
                    return other.socketDir == null;
                } else {
                    return socketDir.equals(other.socketDir);
                }
            } else {
                return false;
            }
        }
    }

    public Registry createRegistry() throws RemoteException {
        if (registry != null) {
            throw new RemoteException("The Registry is already created: "
                    + registry);
        }
        this.registry = LocateRegistry.createRegistry(registryPort,
                socketFactory, socketFactory);
        final PortAssigner ass = new PortAssignerImpl();
        UnicastRemoteObject
                .exportObject(ass, AFUNIXRMIPorts.PORT_ASSIGNER_PORT,
                        socketFactory, socketFactory);
        rebindPortAssigner(ass);
        return registry;
    }

}
