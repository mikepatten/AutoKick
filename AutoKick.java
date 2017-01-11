import com.ibm.OS4690.ControllerApplicationServices;
import com.ibm.OS4690.FlexosException;
import java.io.*;
import java.util.Date;
import java.util.Properties;
import java.text.SimpleDateFormat;

public class AutoKick {
    static boolean running = true;
    static boolean debugging = false;
    static short timeOut = 900; // default is 15 minutes;
    static String logFileName = "AutoKick.log";
    static String dateFormat = "yyyy/MM/dd HH:mm:ss";
    static String propsFileName = "AutoKick.properties";
    static FileInputStream propsFileInput = null;
    static FileOutputStream propsFileOutput = null;
    static File propsFile = new File(propsFileName);
    static Properties defaultProps = new Properties();
    static Properties props = new Properties(defaultProps);

    public static void log(String theMessage){
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat);
        System.out.println(formatter.format(new Date()) + " " + theMessage);
    }

    public AutoKick() {}

    public static void main(String args[]) {
        int inactiveTerminals = 0;
        boolean active = true;
        boolean wasActive = false;
        defaultProps.setProperty("debugging", "false");
        defaultProps.setProperty("timeOut", "900");
        // Create the log file and redirect stdout to it.
        try {
            System.setOut(new PrintStream(logFileName));
        } catch (FileNotFoundException e) {
            System.out.println(e + " File not found: " + logFileName);
            running = false;
        }

        // check to see if the properties file exists, if so, read it
        if (propsFile.exists() && propsFile.isFile()) {
            try {
                propsFileInput = new FileInputStream(propsFileName);
                props.load(propsFileInput);
                if (props.containsKey("timeOut")) {
                    timeOut = Short.valueOf(props.getProperty("timeOut"));
                    if ((timeOut < 15) || (timeOut > 3600)) {
                        timeOut = 900;
                        props.setProperty("timeOut", "900");
                    }
                } else
                    props.setProperty("timeOut", "900");
                if (props.containsKey("debugging")) {
                    if (props.getProperty("debugging").equalsIgnoreCase("true")) {
                        debugging = true;
                    } else {
                        debugging = false;
                        props.setProperty("debugging", "false");
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                running = false;
            } finally {
                if (props != null) {
                    try {
                        propsFileInput.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        running = false;
                    }
                }
            } try {
                propsFileInput.close();
            } catch (IOException ex) {
                ex.printStackTrace();
                running = false;
            } // end try/catch
        }
        // finally, save the properties to the file.
        try {
            propsFileOutput = new FileOutputStream(propsFileName);
            props.store(propsFileOutput, "For PCI compliance, set timeOut to 15 minutes (900 seconds) or less.");
            propsFileOutput.close();
        } catch (IOException ex) {
            ex.printStackTrace();
            running = false;
        } finally {
            if (props != null) {
                try {
                    propsFileOutput.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                    running = false;
                }
            }
        }

        while (running) {
            if (active) {
                try {
                    ControllerApplicationServices.logError('P', 1, 5,  1, new String("time " + timeOut).getBytes());
                } catch (FlexosException e) {

                } catch (com.ibm.OS4690.InvalidParameterException ipe) {
                    log("Invalid parameter in logError() 107");
                }
            }
            try {
                if (debugging && active) log("Waiting for inactivity (" + timeOut + " seconds)");
                wasActive = active; // Was there an active user last time through?
                active = true;      // Assume there is this time
                inactiveTerminals = ControllerApplicationServices.startInactivityMonitoring(timeOut);
            } catch (FlexosException e) {
                int theException = e.getReturnCode();
                switch (theException) {
                case -1302:
                    // Number of seconds to wait for inactivity out of range
                    log("Timeout " + timeOut + " out of range: changing to 900 seconds (15 minutes");
                    timeOut = 900; //set timeOut to default of 15 minutes and keep running
                    break;
                case -1304:
                    if (debugging && wasActive) log("No active users on any console");
                    // I added wasActive so it didn't spam the logs with -1304 exceptions
                    active = false;
                    // No active users on any consoles (system or auxiliary)
                    // ignore this and check over and over
                    break;
                // these are fatal -- deliberate fall through
                case -1101:
                    // Not a background applicaton
                case -1301:
                    // Keyboard and Mouse wait already in progress - somehow we're checking more than once
                default:
                    // Uncaught exception, die
                    log("Fatal exception " + theException);
                    running = false;
                } // end switch
            } // end try/catch startInactivityMonitoring()
            if (active) {
                try {
                    if ((inactiveTerminals & 0x8000) != 0) {
                        log("System console inactive. Logging off");
                        try {
                            ControllerApplicationServices.logError('P', 2, 5, 5, new String("console 0").getBytes());
                        } catch (FlexosException e) {

                        } catch (com.ibm.OS4690.InvalidParameterException ipe) {
                            log("logError() invalid paramter 150");
                        }
                        ControllerApplicationServices.signOffConsoleActiveUser((short)0); // Sign off system console
                    }
                    if ((inactiveTerminals & 0x00FF) != 0) {
                        short inactiveAuxTerminal = (short)(inactiveTerminals & 0x00FF);
                        log("Auxiliary console " + inactiveAuxTerminal + " inactive. Logging off");
                        try {
                            ControllerApplicationServices.logError('P', 2, 5, 5, new String("console " + inactiveAuxTerminal).getBytes());
                        } catch (FlexosException e) {

                        } catch (com.ibm.OS4690.InvalidParameterException ipe) {
                            log("logError invalid paramter 162");
                        }
                        ControllerApplicationServices.signOffConsoleActiveUser(inactiveAuxTerminal); // Sign off auxiliary terminal
                    } // end if auxiliary console
                } catch (FlexosException e) {
                    int theException = e.getReturnCode();
                    try {
                        ControllerApplicationServices.logError('P', 3, 1, 5, new String(Integer.toString(theException)).getBytes());
                    } catch (FlexosException ex) {

                    } catch (com.ibm.OS4690.InvalidParameterException ipe) {
                        log("Invalid logError parameter 173");
                    }
                    switch (theException){
                    case -1304:
                        log(theException + "No active user on this console");
                        // request not valid when there's no active user on this console
                        // I'm ignoring this case for now, I'll look into it further later
                        break;
                    // all these cases are fatal -- deliberate fall through
                    case -1101:
                        // not a background application
                    case -1302:
                        // Invalid console requested
                    case -1303:
                        // request not valid for system console on controller/terminal in terminal mode
                        // shouldn't happen on an auxiliary console
                    case -1305:
                        // request not valid when there's no auxiliary console configured for this console
                        // somehow we tried to sign off an auxiliary console that doesn't exist
                    default:
                        // unhandled exception
                        log("Fatal exception: " + theException);
                        running = false;
                        break;
                    } // end switch
                } // end try/catch
            } // end if (active)
        } // end while (running)
    } // end main
} // end class AutoKick
