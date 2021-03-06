package xtc.jenkins.extensivetesting;

import hudson.FilePath;
import hudson.Launcher;
import hudson.console.HyperlinkNote;
import hudson.model.Run;
import hudson.model.TaskListener;
import xtc.jenkins.extensivetesting.entities.Session;
import xtc.jenkins.extensivetesting.entities.Test;
import xtc.jenkins.extensivetesting.tools.Compressor;
import xtc.jenkins.extensivetesting.tools.Const;
import xtc.jenkins.extensivetesting.tools.Hasher;
import xtc.jenkins.extensivetesting.tools.Logger;
import xtc.jenkins.extensivetesting.webservices.RestRequester;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by Blaise Cador on 20/06/2016.
 */
public class ExtensiveTestingTester {
    private String testPath;
    private String login;
    private String password;
    private String serverUrl;
    private String projectName;
    private Boolean debug;


    public ExtensiveTestingTester(String testPath, String login, String password, String serverUrl,
                                  String projectName, Boolean debug) {
        this.testPath = testPath;
        this.login = login;
        this.password = password;
        this.serverUrl = serverUrl;
        this.projectName = projectName;
        this.debug = debug;
    }

    /**
     * Execute the extensive testing job
     *
     * @param build
     * @param workspace
     * @param launcher
     * @param listener
     * @return
     */
    public Boolean perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException {

        File logFile = new File(workspace + Const.LOG_NAME);
        String jobName = build.getParent().getName().replace(" ", "%20"); // replace job name spaces for html link
        DateFormat dateFormat = new SimpleDateFormat(Const.DATEFORMAT); // get current date time with Calendar()
        Calendar calendar = Calendar.getInstance(); // get current date time with Calendar()
        String now = dateFormat.format(calendar.getTime()); // get current date time with Calendar()
        String reportName = now + Const.REPORT_SUFIX; // html report file name
        String encryptedPassword = Hasher.sha1(password); // hash password with sha1

        Logger logger = Logger.instance(logFile, listener, debug); // Singleton


        Test test = new Test(testPath, projectName, login, encryptedPassword);
        final RestRequester restRequester = new RestRequester(serverUrl, test); // Rest requester
        Session session = new Session();

        // API Results Methods
        String login;
        String testRun;
        String testStatus;
        Boolean testIsRunning;
        String testVerdict;
        String testReport;
        String logout;
        String report = null;
        String printReport = "#### " + testPath + "###\n";
        Boolean printable = true;
        Boolean debugmod = false;


        // Initialize logger first line
        logger.write(printReport, debugmod);
        /***** Starts the test and stores the results *****/
        // Login
        login = restRequester.login();
        org.json.JSONObject jsonLogin = new org.json.JSONObject(login);
        session.setSession_id(jsonLogin.getString(Const.SESSION_ID));
        logger.write(login, debugmod); // Raw json


        if (login == null || session.getSession_id().isEmpty() || "".equals(session.getSession_id())) {
            logger.write(Const.AUTHFAILED, debugmod);
        } else {
            restRequester.setSessionID(session.getSession_id());

            // Launch test

            testRun = restRequester.testRun();
            org.json.JSONObject jsonLaunchTest = new org.json.JSONObject(testRun);
            test.setTestId(jsonLaunchTest.getString(Const.TEST_ID));
            logger.write(testRun, debugmod); // Raw json


            // Get test status

            do {
                testStatus = restRequester.testStatus();
                org.json.JSONObject jsonObject = new org.json.JSONObject(testStatus);
                testStatus = jsonObject.getString(Const.TEST_STATUS);
                System.out.println(testStatus);

                if (test.getTestId().equals(jsonObject.getString(Const.TEST_ID))) {
                    if (Const.RUNNING.equals(testStatus) || Const.NOTRUNNING.equals(testStatus) ) {
                        testIsRunning = true;
                    } else {
                        testIsRunning = false;
                    }
                }else{
                    throw new IOException();
                }
            } while (testIsRunning);

            test.setTestStatus(testStatus);



            // Get test verdict

            testVerdict = restRequester.testVerdict();
            org.json.JSONObject jsonTestVerdict = new org.json.JSONObject(testVerdict);
            test.setTestVerdict(jsonTestVerdict.getString(Const.TEST_RESULT));
            logger.write(testVerdict, debugmod); // Raw json


            // Get test report

            testReport = restRequester.testReport();
            org.json.JSONObject jsonTestReport = new org.json.JSONObject(testReport);
            report = jsonTestReport.getString(Const.TEST_REPORT);
            test.setTestReport(report);
            logger.write(testReport, debugmod); // Raw json


            // Logout

            logout = restRequester.logout();
            org.json.JSONObject jsonLogout = new org.json.JSONObject(logout);
            session.setMessage(jsonLogout.getString(Const.MESSAGE));
            logger.write(logout, debugmod); // Raw json

        }


        /***** store the results in html report file *****/
        // Write HTML Report File
        String htmlContent = Compressor.inflater(report);
        File file = new File(workspace + "/" + reportName);
        try {
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(htmlContent);
            fileWriter.close();
        } catch (IOException exception) {
            System.out.println(Const.FILE_ERR + exception.getMessage());
        }


        /***** store the results in String*****/
        printReport += "Logging" + "\n";
        printReport += "Session = " + test.getTestId() + "\n";
        printReport += "Launching test" + "\n";
        printReport += "Status = " + test.getTestStatus() + "\n";
        printReport += "Getting test verdict" + "\n";
        printReport += "Verdict = " + test.getTestVerdict() + "\n";
        printReport += "Getting test report" + "\n";
        printReport += "Build duration = " + build.getDurationString() + "\n";
        printReport += "Message = " + session.getMessage() + "\n";
        printReport += HyperlinkNote.encodeTo("/job/" + jobName + "/ws/" + reportName,"Test Report") + "\n";
        printReport += HyperlinkNote.encodeTo("/job/" + jobName + "/ws/" + "log.txt","Log file") + "\n";


        /***** Print results and log pages *****/
        logger.write(printReport, printable);

        return Const.PASS.equals(test.getTestVerdict());


    }

}
