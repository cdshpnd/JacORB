package test.orbreinvoke.tao_ns;

import java.util.Properties;
import java.io.*;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import org.jacorb.naming.NameServer;
import org.jacorb.orb.util.*;
import org.jacorb.util.ObjectUtil;
import org.omg.CORBA.BAD_PARAM;
import org.omg.CORBA.ORB;
import org.omg.CORBA.Policy;
import org.omg.PortableServer.IdAssignmentPolicyValue;
import org.omg.PortableServer.LifespanPolicyValue;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;
import org.omg.PortableServer.Servant;
import org.omg.CosNaming.*;
import org.omg.CosNaming.NamingContextPackage.*;

import org.omg.PortableServer.ImplicitActivationPolicyValue;
import test.listenendpoints.echo_corbaloc.EchoMessageImpl;
import test.listenendpoints.echo_corbaloc.ListenEndpoints;
import test.listenendpoints.echo_corbaloc.Endpoint;
import test.listenendpoints.echo_corbaloc.CmdArgs;


public class Server
{
    public static void main(String[] args)
    {
        try
        {
            CmdArgs cmdArgs = new CmdArgs("Server", args);
            boolean cmdArgsStatus = cmdArgs.processArgs();
            // translate any properties set on the commandline but after the
            // class name to a properties
            java.util.Properties props = ObjectUtil.argsToProps(args);
            String implName = props.getProperty("jacorb.implname", "EchoServerX");
            System.out.println("SERVER: jacorb.implname: <" + implName + ">");
            if (implName.equals("EchoServerX"))
            {
                props.setProperty("jacorb.implname", implName);
            }

            String objectId = implName + "-ID";
            String poaName = "EchoServer-POA";

            //init ORB
            ORB orb = ORB.init(args, props);
            //init POA
            POA rootPOA = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            //init new POA
            Policy[] policies = new Policy[2];
            policies[0] = rootPOA.create_lifespan_policy(LifespanPolicyValue.PERSISTENT);
            policies[1] = rootPOA.create_id_assignment_policy(IdAssignmentPolicyValue.USER_ID);

            for (int i=0; i<policies.length; i++)
            {
                policies[i].destroy();
            }

            POA poa = rootPOA.create_POA
                (poaName, rootPOA.the_POAManager(), policies);

            poa.the_POAManager().activate();

            // Find the Naming Service
            System.out.println("SERVER: call orb.resolve_initial_references( \"NameService\" )" );
            org.omg.CORBA.Object nsObj =
                orb.resolve_initial_references( "NameService" );

            System.out.println("SERVER: call NamingContextExtHelper.narrow( nsObj )" );
            NamingContextExt nsRootContext =
                NamingContextExtHelper.narrow( nsObj );

            if (nsRootContext == null) {
                System.err.println("SERVER: NameService's context is null" );
                System.exit(1);
            }

            NameComponent[] context = new NameComponent[1];
            context[0] = new NameComponent(implName, "context");
            try
            {
                nsRootContext.unbind(context);

            }
            catch (Exception e)
            {
                // ignore and  do nothing
            }

            try
            {

                System.out.println("SERVER: call nsRootContext.resolve(context)" );
                nsRootContext.resolve(context);
            }
            catch (NotFound e)
            {
                System.out.println("SERVER: got NotFound exception, " + e.getMessage());
            }

            // create servant object
            EchoMessageImpl echoServant = new EchoMessageImpl(implName + "." + objectId);
            poa.activate_object_with_id(objectId.getBytes(), echoServant);
            org.omg.CORBA.Object ref = poa.servant_to_reference(echoServant);
            System.out.println("SERVER: call nsRootContext.rebind(context, ref)");
            try
            {
                nsRootContext.rebind(context, ref);
            }
            catch (NotFound e)
            {
                System.out.println("SERVER: got NotFound exception, " + e.getMessage());
            }

            String ior = orb.object_to_string(ref);
            System.out.println("SERVER IOR: " + ior);
            System.out.flush();


            if (cmdArgs.getIORFile() != null)
            {
                PrintWriter ps = new PrintWriter(new FileOutputStream(
                new File( cmdArgs.getIORFile())));
                ps.println(ior);
                ps.close();
            }

            // wait for requests
            orb.run();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
   }
}
