/* ****************************************************************************
 * This code is part of jCMPL
 *
 * Copyright (C) 2013 Mike Steglich / B. Knie Technical University of Applied
 * Sciences Wildau, Germany
 *
 * pyCMPL is a project of the Technical University of Applied Sciences Wildau
 * and the Institute for Operations Research and Business Management at the
 * Martin Luther University Halle-Wittenberg.
 *
 * Please visit the project homepage <http://www.coliop.org>
 *
 * pyCMPL is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * pyCMPL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public # License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses/>.
 * ****************************************************************************/

package jCMPL;

import java.io.BufferedReader;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import java.net.URL;
import java.util.ArrayList;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;



/**
 * Cmpl 
 * @author      Mike Steglich
 * @author      Bernhard Knie
 * @version     1.0.0 beta 1
 * license      LGPLv3
 */
public class Cmpl extends Thread {
    
    public static final int CMPL_UNKNOWN = 0;
    public static final int CMPL_OK = 1;
    public static final int CMPL_WARNINGS = 2;
    public static final int CMPL_FAILED = 3;
    public static final int SOLVER_OK = 4;
    public static final int SOLVER_FAILED = 5;
    
    public static final int CMPLSERVER_OK = 6;
    public static final int CMPLSERVER_ERROR = 7;
    public static final int CMPLSERVER_CLEANED = 8;
    
    public static final int PROBLEM_RUNNING = 9;
    public static final int PROBLEM_FINISHED = 10;
    public static final int PROBLEM_CANCELED = 11;
    public static final int PROBLEM_NOTRUNNING = 12;
    
    public static final int SYNCHRONOUSLY = 13;
    public static final int ASYNCHRONOUSLY = 14;

    private int _compatibility;
    
    private String _problem;
    private String _cmplDataFile;
    private String _cmplMsgFile;
    private String _cmplSolFile;
    private String _cmplDataStr;
    private ArrayList<CmplSet> _setList;
    private ArrayList<CmplParameter> _parameterList;

    private HashMap<Integer, String> _optionsList;
    private CmplMessages _status;
    private CmplSolutions _solutions;
    private String _solutionString;
      
    private CmplInfo _cmplInfos;
    private String _cmplInfoString;

    private boolean _remoteMode;
    private int _remoteStatus;
    private static String _cmplUrl;
    private String _jobId;
    private int _maxCmplServerTries;
    private static XmlRpcClient _cmplServer;
    private boolean _cmplServerRunning;
        
    private Process _cmplProc;
    
    private long _refreshTime;
    private boolean _printOutput;
    private String _outputString;
    private int _id;
    private String _outputLeadString;
    private String _model;
    
    private boolean _isCleaned;
    private boolean _debug;
    private boolean _runCanceled;
    
    
  
    
    /**
     * constructor
     *
     */
    public Cmpl(String model) throws CmplException  {

        _compatibility = 1; // CMPL 1.9 (contains pyCmpl >= 1.2 and jCmpl >= 1.0)
        
        _problem = "";
        _cmplDataFile = "";
        _cmplMsgFile = "";
        _cmplSolFile = "";
        _cmplDataStr = "";

        _setList = new ArrayList<CmplSet>();
        _parameterList = new ArrayList<CmplParameter>();
   
        _optionsList = new HashMap<Integer, String>();
        _status = new CmplMessages();
        _solutions = new CmplSolutions();
        _solutionString = "";

        _cmplInfos = null;
        _cmplInfoString = "";

        _remoteMode = false;
        _remoteStatus = CMPL_UNKNOWN;
        _cmplUrl = "";
        _jobId = "";
        _maxCmplServerTries = 10;
        _cmplServer = null;
        _cmplServerRunning = false;

        _cmplProc = null;

        _refreshTime = 400;
        _printOutput = false;
        _outputString = "";
        _model = model;
        
        _isCleaned=false;
        _debug = false;
        
        Locale.setDefault(Locale.ENGLISH);

        File ModelFile = new File(_model);


        int Min = 100000;
        int Max = 999999;

        if (ModelFile.exists()) {
            _id = Min + (int) (Math.random() * ((Max - Min) + 1));
            _outputLeadString = ModelFile.getName().substring(0, ModelFile.getName().lastIndexOf('.')) + "> ";
        } else {
            throw new CmplException("Cannot read CMPL file: " + _model);
        }
    }
    
    /**
     * destructor
     *
     */
    protected void finalize( ) throws Throwable {
        try {
            if (!_isCleaned) {
                try {
                    cleanUp();
                } catch (Exception e) {
                    // foo
                }
                
            }
        } finally {            
            super.finalize();
        }
    }
    

    /**
     * Cleans up all temp elements 
     *
     */
    private void cleanUp() throws CmplException {
        if (_debug) {
            try {
                System.out.println("Hit Enter to exit");
                System.in.read();
            } catch (IOException ex) {
                throw new CmplException("Internal error: "  + ex);
            }
        }
        if (_remoteMode) {
            if (_remoteStatus != PROBLEM_FINISHED && _remoteStatus != CMPLSERVER_ERROR && _remoteStatus == PROBLEM_CANCELED) {
                if (_cmplServerRunning) {
                    cmplServerExecute("cancel", new Object[]{_jobId});
                }
            }
            if (_remoteStatus != CMPLSERVER_CLEANED) {
                cmplServerExecute("removeProblem", new Object[]{_jobId});
                _remoteStatus = CMPLSERVER_CLEANED;
            }
        } else {
            if (_cmplProc!=null){
                _cmplProc.destroy();
            }
        }
        
        CmplTools.delTmpFile(_cmplDataFile);
        CmplTools.delTmpFile(_cmplMsgFile);
        CmplTools.delTmpFile(_cmplSolFile);
        
        _isCleaned=true;
    }

    
    //Getter
    
    /**
     * Returns the name of the model
     * @return  The name of the mode
     */
    public String model() {
        return _model;
    }

     /**
     * Returns the refreshtime of the model
     *
     * @return Refreshtime of the model
     */
    public long refreshTime() {
        return _refreshTime;
    }

    /**
     * Returns the stdOut and stdErr of Cmpl and the invoked solver
     * @return The stdOut and stdErr of Cmpl and the invoked solver
     */
    public String output() {
        return _outputString;
    }

    /**
     * Returns the CmplMessages
     * @return ArrayList of the CmplMessages
     */
    public ArrayList<CmplMsg> cmplMessages() {
        return _status.cmplMessageList();
    }
   
    /**
     * Returns a list of CmplSolutions objects 
     * @return ArrayList of CmplSolutions objects
     * @throws CmplException 
     */
    public ArrayList<CmplSolution> solutionPool() throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            return _solutions.solutions();
        } else {
            throw new CmplException("No solution found so far");
        }
    }
    
    /**
     * Returns the first (optimal) CmplSolutions object
     * @return first (optimal) solution 
     * @throws CmplException 
     */
    public CmplSolution solution() throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            return _solutions.solution();
        } else {
            throw new CmplException("No solution found so far");
        }
    }
   
    /**
     * Returns the number of variables of the generated and solved CMPL model
     * @return Number of variables
     * @throws CmplException 
     */
    public long nrOfVariables() throws CmplException {
         if (_solutions.nrOfSolutions() > 0) {
            return _solutions.nrOfVariables();
        } else {
            throw new CmplException("The model isn't generated yet.");
        }
    }
    
    /**
     * Returns the number of constraints of the generated and solved CMPL model
     * @return Number of constraints
     * @throws CmplException 
     */
    public long nrOfConstraints() throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            return _solutions.nrOfConstraints();
        } else {
            throw new CmplException("The model isn't generated yet.");
        }
    }
    
    /**
     * Returns the name of the objective function of the generated and solved CMPL model
     * @return objective name
     * @throws CmplException 
     */
    public String objectiveName() throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            return _solutions.objectiveName();
        } else {
            throw new CmplException("No solution found so far");
        }
    }
    
    /**
     * Returns the objective sense of the generated and solved CMPL model
     * @return objective sense
     * @throws CmplException 
     */
    public String objectiveSense() throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            return _solutions.objectiveSense();
        } else {
            throw new CmplException("No solution found so far");
        }
    }

    /**
     * Returns the number of solutions of the generated and solved CMPL model
     * @return Number of solutions
     * @throws CmplException 
     */
    public long nrOfSolutions() throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            return _solutions.nrOfSolutions();
        } else {
            throw new CmplException("No solution found so far");
        }
    }

    /**
     * Returns the name of the invoked solver of the generated and solved CMPL model
     * @return Invoked solver
     * @throws CmplException 
     */
    public String solver() throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            return _solutions.solver();
        } else {
            throw new CmplException("Since the model isn't solved the solver is not known.");
        }
    }

    /**
     * Returns the message of the invoked solver of the generated and solved CMPL model
     * @return Message of the invoked solver
     * @throws CmplException 
     */
    public String solverMessage() throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            return _solutions.solverMessage();
        } else {
            throw new CmplException("Since the model isn't solved the solver message is not known.");
        }
    }

    /**
     * Returns the a string with the display options for the variables of the generated and solved CMPL model
     * @return Display options for the variables
     * @throws CmplException 
     */
    public String varDisplayOptions() throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            return _solutions.varDisplayOptions();
        } else {
            throw new CmplException("Since the model isn't solved this option isn't known.");
        }
    }

    /**
     * Returns the a string with the display options for the constraints of the generated and solved CMPL model
     * @return Display options for the constraints
     * @throws CmplException 
     */
     public String conDisplayOptions() throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            return _solutions.conDisplayOptions();
        } else {
            throw new CmplException("Since the model isn't solved this option isn't known.");
        }
    }
    
     /**
      * Returns the CMPL related status of the Cmpl object
      *     CMPL_UNKNOWN = 0
      *     CMPL_OK = 1
      *     CMPL_WARNINGS = 2
      *     CMPL_FAILED = 3
      *     CMPLSERVER_OK = 6
      *     CMPLSERVER_ERROR = 7
      *     CMPLSERVER_CLEANED = 8 
      *     PROBLEM_RUNNING = 9
      *     PROBLEM_FINISHED = 10
      *     PROBLEM_CANCELED = 11 
      *     PROBLEM_NOTRUNNING = 12 
      * @return  Status of the Cmpl object
      */
    public int cmplStatus() {
        if (_remoteMode) {
            if (_remoteStatus == CMPL_UNKNOWN) {
                return _status.cmplStatus();
            } else {
                return _remoteStatus;
            }
        } else {
            if (_status.cmplStatus() == CMPL_UNKNOWN) {
                return CMPL_UNKNOWN;
            } else {
                return _status.cmplStatus();
            }
        }
    }
        
    /**
     * Returns the CMPL related status text of the Cmpl object
     *  CMPL_UNKNOWN 
     *  CMPL_OK     
     *  CMPL_WARNINGS
     *  CMPL_FAILED 
     *  SOLVER_OK 
     *  SOLVER_FAILED 
     *  CMPLSERVER_OK 
     *  CMPLSERVER_ERROR 
     *  CMPLSERVER_CLEANED  
     *  PROBLEM_RUNNING 
     *  PROBLEM_FINISHED 
     *  PROBLEM_CANCELED  
     *  PROBLEM_NOTRUNNING  
     * @return Cmpl status text
     */
    public String cmplStatusText()
    {
        String ret="";
        if(_remoteMode && _remoteStatus != CMPL_UNKNOWN) {
            if (_remoteStatus == CMPLSERVER_OK) {
                ret =  "CMPLSERVER_OK";
            } else if (_remoteStatus == CMPLSERVER_ERROR) {
                ret =   "CMPLSERVER_ERROR";
            } else if (_remoteStatus == PROBLEM_RUNNING) {
                ret =   "PROBLEM_RUNNING";
            } else if (_remoteStatus == PROBLEM_FINISHED) {
                ret =   "PROBLEM_FINISHED";
            } else if (_remoteStatus == PROBLEM_CANCELED) {
                ret =   "PROBLEM_CANCELED";
            } else if (_remoteStatus == PROBLEM_NOTRUNNING) {
                ret =   "PROBLEM_NOTRUNNING";
            }
        } else {
            if (_status.cmplStatus() == CMPL_UNKNOWN) {
                ret =   "CMPL_UNKNOWN";
            } else if (_status.cmplStatus() == CMPL_OK) {
                ret =   "CMPL_OK";
            } else if (_status.cmplStatus() == CMPL_WARNINGS) {
                ret =   "CMPL_WARNINGS";
            } else if (_status.cmplStatus() == CMPL_FAILED) {
                ret =   "CMPL_FAILED";
            }
        }
        return ret;
    }
    
    /**
     * Returns the solver related status of the Cmpl object
     *  SOLVER_OK = 4
     *  SOLVER_FAILED = 5
     * @return solver status
     */
    public int solverStatus() {
        if (_solutions.nrOfSolutions() == 0) {
            return SOLVER_FAILED;
        } else {
            return SOLVER_OK;
        }
    }

    /**
     * Returns the solver related status text of the Cmpl object
     *  SOLVER_OK 
     *  SOLVER_FAILED 
     * @return solver status text
     */
    public String solverStatusText() {
        if (_solutions.nrOfSolutions() == 0) {
            return "SOLVER_FAILED";
        } else {
            return "SOLVER_OK";
        }
    }
    
     /**
     * Returns the name of the CmplSolution file - controlled by the command
     * line arg -solution
     *
     * @return name of the CmplSolution file
     */
    public String cmplSolFile() {
        return _solutions.cmplSolFile();
    }

    /**
     * Returns the name of the Csv solution file - controlled by the command
     * line arg -solutionCsv
     *
     * @return name of the Csv solution file
     */
    public String csvSolFile() {
        return _solutions.csvSolFile();
    }

    /**
     * Returns the name of the Ascii solution file - controlled by the command
     * line arg -solutionAscii
     *
     * @return name of the Ascii solution file
     */
    public String asciiSolFile() {
        return _solutions.asciiSolFile();
    }

    /**
     * Returns the jobId of the Cmpl problem at the connected CMPLServer
     * @return String of the jobId
     */
    public String jobId() {
        return _jobId;
    }
    
    //Setter
    
    /**
     * Turns the output of CMPL and the invoked solver on or off
     * @param ok    Switch for on or off
     * @param lStr  Leading string for the output (default - model name)
     */
    public void setOutput(Boolean ok, String lStr) {
        _printOutput = ok;
        if (!lStr.isEmpty()) {
            _outputLeadString = lStr;
        }
    }
    
    /**
     * Turns the output of CMPL and the invoked solver on or off
     * @param ok    Switch for on or off
     */
    public void setOutput(Boolean ok) {
        setOutput(ok, "");
    }

    /**
     * Refresh time for getting the output of CMPL and the invoked solver 
     * from a CMPLServer if the model is solved synchronously.  
     * @param rTime refresh time in milliseconds (default 400)
     */
    public void setRefreshTime(long rTime) {
        _refreshTime = rTime;
    }

    /**
     * Committing a CmplSet object to the Cmpl model
     * @param set  CmplSet object
     * @throws CmplException 
     */
    private void setSet(CmplSet set) throws CmplException {
        if (set.len() > 0) {
            _setList.add(set);
        } else {
            throw new CmplException("set " + set.name() + " contains no elements ");
        }
    }

    /**
     * Committing CmplSet objects to the Cmpl model
     * @param sets CmplSet object(s)
     * @throws CmplException 
     */
    public void setSets(CmplSet... sets) throws CmplException {
        for (int i = 0; i < sets.length; i++) {
            setSet(sets[i]);
        }
    }

    /**
     * Committing a CmplParameter object to the Cmpl model
     * @param param CmplParameter object
     * @throws CmplException 
     */
    private void setParameter(CmplParameter param) throws CmplException {
        if (param.len() > 0) {
            _parameterList.add(param);
        } else {
            throw new CmplException("set " + param.name() + " contains no elements ");
        }
    }

    /**
     * Committing CmplParameter objects to the Cmpl model
     * @param params CmplParameter object(s)
     * @throws CmplException 
     */
    public void setParameters(CmplParameter... params) throws CmplException {
        for (int i = 0; i < params.length; i++) {
            setParameter(params[i]);
        }
    }

    /**
     * Sets a CMPL, display or solver option
     * @param option option in CmplHeader syntax
     * @return option id
     */
    public Integer setOption(String option) {
        Integer pos = _optionsList.size();
        _optionsList.put(pos, option);
        return pos;
    }

    /**
     * Deletes an option
     * @param pos option id
     * @throws CmplException 
     */
    public void delOption(Integer pos) throws CmplException {
        _optionsList.remove(pos);
    }

    /**
     * Deletes all options
     */
    public void delOptions() {
        _optionsList.clear();
    }

    /**
     * Very simple debug mode  
     * @param x switchfor on or off
     */
    public void debug(boolean x) {
        _debug=x;
    }
    
    // other methods
    /**
     * Generates am internal CmplData file for the communication with the 
     * CMPL model
     */
    private void cmplDataElements() throws CmplException {
        try {
          
            //_cmplDataStr = "#This cmplData file was generated by jCMPL \n";
            int count;

        
            for ( CmplSet s : _setList) {
                _cmplDataStr += "%" + s.name();

                if (s.rank() > 1) {
                    _cmplDataStr += " set[" + (String.valueOf(s.rank())) + "] < ";
                } else {
                    _cmplDataStr += " set < ";
                }

                if (s.type() == 0) {
                    _cmplDataStr += "\n";
                    count = 1;

                    for (int i = 0; i < s.len(); i++) {

                        _cmplDataStr+=writeElement(s.get(i));
                        
                        if (count == s.rank()) {
                            _cmplDataStr += "\n";
                            count = 1;
                        } else {
                            count += 1;
                        }
                    }
                }
                if (s.type() == 1) {
                    _cmplDataStr += "\n";

                    for (int i = 0; i < s.len(); i++) {
                        for (int j = 0; j < s.rank(); j++) {
                            _cmplDataStr += writeElement(s.get(i,j));
                        }
                        _cmplDataStr += "\n";
                    }
                }

                if (s.type() == 2) {
                    _cmplDataStr += s.get(0) + ".." + s.get(1) + " ";
                }
                if (s.type() == 3) {
                    _cmplDataStr += s.get(0) + "(" + s.get(1) + ")" + s.get(2) + " ";
                }

                _cmplDataStr += ">\n";
            }

           
            for ( CmplParameter p :_parameterList ) {
                _cmplDataStr += "%" + p.name();
                int pos = 0;
                if (p.rank() > 0) {
                    _cmplDataStr += "[";
                    
                     for (CmplSet s: p.setList()) {
                        boolean setFound = false;
                      
                        for (CmplSet j : _setList) {
                            if (s.name().equals(j.name())) {
                                setFound = true;
                                break;
                            }
                        }
                        
                        if (!setFound) {
                            throw new CmplException("The set " + s.name() + " used for the parameter " + p.name() + " doesn't exist.");
                        } else {
                            _cmplDataStr += s.name();

                            if (pos < (p.setList().size() - 1)) {
                                _cmplDataStr += ",";
                            }
                            pos += 1;
                        }
                    }
                    _cmplDataStr += "] <\n";
                    _cmplDataStr += writeListElements(p.values());
                    _cmplDataStr += ">\n";
                } else {
                    _cmplDataStr += " < " + writeElement(p.value()) + " >\n";
                }
            }
            if (!_remoteMode) {
                BufferedWriter out = new BufferedWriter(new FileWriter(_cmplDataFile));
                out.write(_cmplDataStr);
                out.close();
            }
        } catch (Exception e) {
             throw new CmplException("Internal error in " + getClass().getName() + "\n " + e);
        }

    }
    
    /**
     * Internal function - used by cmplDataElements and writeListElements
     * Writes an set or parameter value into a string
     * @param val -set or parameter value
     * @return a value string
     */
    private String writeElement(Object val) throws CmplException {
        if (val==null) { 
            throw new CmplException("Parameter or set w/o value " );
        }
       
        String tmpStr = "";
        if (val.getClass().toString().contains("String")) {
            tmpStr += "\"" + val + "\" ";
        } else {
            tmpStr += val + " ";
        }
         return tmpStr;

    }
    
    /**
     * Writes a list of parameters into a CmplDataFile 
     * @param val list of parameters
     * @return a value string
     * @throws CmplException 
     */
    private String writeListElements(Object val) throws CmplException {
        String tmpStr = "";
        if (val instanceof int[]) {
            for (int i=0; i < ((int[])val).length; i++ ) {
                tmpStr += writeElement( ((int [])val)[i] ) ;
            }
        } else if  (val instanceof long[]) {
            for (int i=0; i < ((long[])val).length; i++ ) {
                tmpStr += writeElement( ((long [])val)[i] ) ;
            }
        } else if (val instanceof float[]) {
            for (int i=0; i < ((float[])val).length; i++ ) {
                tmpStr += writeElement( ((float [])val)[i] ) ;
            }
        } else if (val instanceof double[]) {
            for (int i = 0; i < ((double[]) val).length; i++) {
                tmpStr += writeElement(((double[]) val)[i]);
            }
        } else if (val.getClass().toString().contains("List") || val.getClass().isArray()) {
            if (val.getClass().toString().contains("List")) {
                for (int i = 0; i < ((ArrayList) val).size(); i++) {
                    if (((ArrayList) val).get(i).getClass().toString().contains("List") || ((ArrayList) val).get(i).getClass().isArray()) {
                        tmpStr += writeListElements(((ArrayList) val).get(i));
                    } else {
                        tmpStr += writeElement(((ArrayList) val).get(i));
                    }
                }
            } else if (val.getClass().isArray()) {
                for (int i = 0; i < ((Object[]) val).length; i++) {
                    if (((Object[]) val)[i].getClass().toString().contains("List") || (((Object[]) val)[i].getClass().isArray())) {
                        tmpStr += writeListElements(((Object[]) val)[i]);
                    } else {
                        tmpStr += writeElement(((Object[]) val)[i]);
                    }
                }
            }
        } else {
            tmpStr += writeElement(val);
        } 
    
    tmpStr += "\n" ;
    return tmpStr;
    }
    
    /**
     * Returns a CmplSolution obj out of the solution list
     * @param solNr Nr. of the solution
     * @return CmplSolution obj
     * @throws CmplException
     */
  /*  private CmplSolution solByNr(int solNr) throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            if (solNr <= _solutions.nrOfSolutions()) {
                return _solutions.solutions().get(solNr);
            } else {
                throw new  CmplException("Solution with number: " + String.valueOf(solNr) + " doesn't exist.");
            }
        } else {
             throw new CmplException("No solution found so far");
        }
    }*/

    /**
     * Enables the direct access to a single variable or variable array 
     * of the optimal solution
     * @param name Name of the variable (w/o index)
     * @return either a CmplSolElement or CmplSolArray 
     * @throws CmplException 
     */
    public Object getVarByName(String name) throws CmplException {
        return  getVarByName(name,0);
    }
    
    /**
     * Enables the direct access to a single variable or variable array 
     * of solution with solNr
     * @param name Name of the variable (w/o index)
     * @param solNr Index of the solution
     * @return either a CmplSolElement or CmplSolArray 
     * @throws CmplException 
     */
    public Object getVarByName(String name, int solNr) throws CmplException {
        return  getElementByName(name,_solutions.solutions().get(solNr).variables());
    }
    
    /**
     * Enables the direct access to a single constraint or constraint array 
     * of the optimal solution
     *
     * @param name Name of the constraint (w/o index)
     * @return either a CmplSolElement or CmplSolArray
     * @throws CmplException
     */
    public Object getConByName(String name) throws CmplException {
        return getConByName(name, 0);
    }
 
    /**
     * Enables the direct access to a single constraint or constraint array 
     * of the solution with solNr
     *
     * @param name Name of the constraint (w/o index)
     * @param solNr Index of the solution
     * @return either a CmplSolElement or CmplSolArray
     * @throws CmplException
     */
    public Object getConByName(String name, int solNr) throws CmplException {
        return getElementByName(name,_solutions.solutions().get(solNr).constraints());
    }
    
    /**
     * Internal function used for getVarByName and getConByName
     * @param name  Var or con name
     * @param solObj Index of the solution
     * @return either a CmplSolElement or CmplSolArray
     * @throws CmplException 
     */
    private Object getElementByName(String name, ArrayList<CmplSolElement> solObj) throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {

            CmplSolArray val = new CmplSolArray();

            CmplSolElement val1 = null;
            boolean isArray = false;

            for (CmplSolElement e : solObj) {
                if (e.name().startsWith(name)) {
                    if (e.name().contains("[")) {
                        if (!isArray) {
                            isArray = true;
                        }
                        val.put(e.name().substring(e.name().indexOf("[") + 1, e.name().indexOf("]")), e);
                    } else {
                        val1 = e;
                        break;
                    }
                }
            }
            if (isArray) {
                return val;
            } else {
                return val1;
            }
        } else {
            throw new CmplException("No solution found so far");
        }
    }

    
    /**
     * Solves a Cmpl model either with a local installed CMPL or if the model
     * is connected with a CMPLServer remotely.
     * The status of the model and the solver can be obtained by the methods 
     * cmplStatus, cmplStatusText,solverStatus and solverStatusText
     * @throws CmplException 
     */
    public void solve() throws CmplException {
  
        if (_remoteMode) {
            
            if (!_cmplServerRunning) {
                throw new CmplException("Model is not connected to a CmplServer");
            }

            _status = new CmplMessages();
            _solutions = new CmplSolutions();

            if (_remoteStatus == CMPLSERVER_CLEANED) {
                connect(_cmplUrl);
            }

            String instStr = send();

            if (_debug) {
                String _instFile = _problem + ".cinst";
                CmplTools.writeAsciiFile(_instFile, instStr);
            }

            while (_remoteStatus != PROBLEM_FINISHED) {
                knock();
                try {
                    Thread.sleep(_refreshTime);
                } catch (InterruptedException ex) {
                    throw new CmplException("internal error: " + ex);
                }
            }
            retrieve();


        } else {
            
            _problem = _model.substring(0, _model.lastIndexOf(".")) ;
            _cmplDataFile = _problem + "_" + _id + ".cdat";
            _cmplMsgFile = _problem + "_" + _id + ".cmsg";
            _cmplSolFile = _problem + "_" + _id + ".csol";
            
            cmplDataElements();
            
            _status = new CmplMessages(_cmplMsgFile);
            _solutions = new CmplSolutions(_cmplSolFile);

            File problemFile = new File(_problem);
            String tmpAlias = problemFile.getName()+ "_" + _id;

            String cmplBin = System.getenv("CMPLBINARY");

            if (cmplBin == null) {

                Properties prop = System.getProperties();
                String os = prop.getProperty("os.name");

                if (os.contains("Mac")) {
                    cmplBin = "/usr/bin/cmpl";
                } else if (os.contains("Linux")) {
                    cmplBin = "/usr/share/Cmpl/bin/cmpl";
                } else if (os.contains("Windows")) {
                    cmplBin = "c:\\program files\\Cmpl\\cmpl.bat";
                    String arch = prop.getProperty("os.arch");
                    if (arch.contains("_64")) {
                        if (!(new File(cmplBin).exists())) {
                            cmplBin = "c:\\program files (x86)\\Cmpl\\cmpl.bat";
                        }
                    }
                } else {
                    throw new CmplException("Your operating system is not supported : " + os);
                }
            }
            File cmplBinFile = new File(cmplBin);
            if (cmplBinFile.exists() && cmplBinFile.canExecute()) {

                ArrayList<String> cmdList = new ArrayList<String>();
                cmdList.add(cmplBin);
                cmdList.add(_model);
                cmdList.add("-solution");
                cmdList.add("-e");
                cmdList.add("-alias");
                cmdList.add(tmpAlias);

                if (!_optionsList.isEmpty()) {
                    for (Map.Entry<Integer, String> o : _optionsList.entrySet()) {
                        cmdList.add("-headerOpt");
                        cmdList.add(o.getValue().replace(" ", "#"));
                    }
                }

                try {
                    ProcessBuilder launcher = new ProcessBuilder(cmdList);
                    launcher.environment().put("CmplJava", "1");
                    launcher.redirectErrorStream(true);
                    _cmplProc = launcher.start();
                    BufferedReader output = new BufferedReader(new InputStreamReader(_cmplProc.getInputStream()));

                    String line;
                    while ((line = output.readLine()) != null) {
                        handleOutput(line);
                    }
                    if (!_runCanceled) {
                        _cmplProc.waitFor();
                    }

                    output.close();

                } catch (IOException ex) {
                    throw new CmplException("Can't execute the CMPL binary : " + ex);
                } 
                
                catch (InterruptedException ex) {
                    throw new CmplException("Internal error : " + ex);
                }
              
                if (_cmplProc.exitValue()!=0)
                    throw new CmplException(_outputString);
                    
                _status.readCmplMessages();

                if (_status.cmplStatus() == CMPL_FAILED) {
                    throw new CmplException("Cmpl finished with errors", _status.cmplMessageList());
                }

                _solutions.readSolution();
                cleanUp();
            } else {
                throw new CmplException("Cant't execute Cmpl binary: " + cmplBin);
            }
        }
    }

    /**
     * Internal function for the handling of the stdOut and stdErr of
     * CMPL and the solver
     * @param oStr  stdOut and stdErr String
     */
    private void handleOutput(String oStr) {
        if (!oStr.isEmpty()) {
            if (_printOutput) {
                if (!_outputLeadString.isEmpty())
                    System.out.println(_outputLeadString + oStr.trim().replace("\n", "\n" + _outputLeadString ));
                else
                    System.out.println(oStr.trim().replace("\n", "\n" + _outputLeadString ));
            }
            _outputString += oStr;
        }
    }

    /**
     * Connects a CMPLServer under cmplUrl - first step of solving a model on a CMPLServer remotely 
     * @param cmplUrl URL of the CMPLServer
     * @throws CmplException 
     */
    public void connect(String cmplUrl) throws CmplException {
        _cmplUrl = cmplUrl;
        _remoteMode = true;
        File file = new File(_model);
        Object[] ret = null;
        
        if (_remoteStatus != CMPL_UNKNOWN) {
            throw new CmplException("Problem is still connected with CMPLServer: at " + cmplUrl + " with jobId " + _jobId);
        }

        try {
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setServerURL(new URL(_cmplUrl));

            _cmplServer = new XmlRpcClient();
            _cmplServer.setConfig(config);

            ret = cmplServerExecute("getJobId", new Object[]{file.getName(), _compatibility}  );
            
        } catch (Exception e) {
            if (e.toString().contains("getJobId()")) {
                //only relevant for pyCmpl 1.0
                throw new CmplException("Incompatible CmplServer - please install a newer CMPLServer");
            } else {
                throw new CmplException(e.toString());
            }
        }
       
        _jobId = (String) ret[2];
        _remoteStatus = (Integer) ret[0];
        
        if (_remoteStatus != CMPLSERVER_ERROR) {
            _cmplServerRunning = true;
            handleOutput(_outputLeadString + "Connected with CmplServer at  " + cmplUrl + " with jobId " + _jobId);
        } else {
            cleanUp();
            throw new CmplException((String) ret[1]);
        }
    }

    /**
     * Disconnects the connected CMPLServer
     * @throws CmplException 
     */
    public void disconnect() throws CmplException {
        _cmplUrl = "";

        if (_remoteStatus != CMPLSERVER_CLEANED) {
            if (_cmplServerRunning) {
                cmplServerExecute("cancel", new Object[]{_jobId});
                _cmplServerRunning = false;
            }
            cmplServerExecute("removeProblem", new Object[]{_jobId});
        }
        _remoteMode = false;
        _remoteStatus = CMPL_UNKNOWN;
    }
    
    /**
     * Sends the Cmpl model instance to  the connected CMPLServer 
     * first step of solving a model on a CMPLServer asynchronously 
     * (after connect())
     * The status of the model can be obtained by the methods
     * cmplStatus and cmplStatusText
     * @throws CmplException 
     */
    public String send() throws CmplException {
        String instStr; 
        
        if (_remoteMode) {
     
            if (!_cmplServerRunning) {
                throw new CmplException("Model is not connected to a CmplServer");
            }
            
            if (_remoteStatus == CMPLSERVER_CLEANED) {
                connect(_cmplUrl);
            }

            knock();
            if (_remoteStatus == PROBLEM_RUNNING) {
                throw new CmplException("Don't send the problem again before the CmplServer finished the previous one");
            }
                 
            _problem = _model.substring(0, _model.lastIndexOf('.'));
            _cmplSolFile = _problem + ".csol";
            
            cmplDataElements();

            _status = new CmplMessages();
            _solutions = new CmplSolutions();
            _cmplInfos= new CmplInfo();

            CmplInstance cmplInstance = new CmplInstance();
            instStr = cmplInstance.cmplInstanceStr(_model, _optionsList, _cmplDataStr, _jobId);

            Object[] ret = cmplServerExecute("send", new Object[]{instStr});

            _remoteStatus = (Integer) ret[0];

            if (_remoteStatus== CMPLSERVER_ERROR) {
                cleanUp();
                throw new CmplException((String) ret[1]);
            }
        } else {
            throw new CmplException("Cmpl.send can only be used in remote mode");
        }
        
        return instStr;
    }
    
    /**
     * Knocks on the door of the connected CMPLServer and asks whether 
     * the model is finished - second step of solving a model on a 
     * CMPLServer asynchronously 
     * The status of the model can be obtained by the methods
     * cmplStatus and cmplStatusText
     * @throws CmplException 
     */
    public void knock() throws CmplException {
        if (_remoteMode) {
            if (!_cmplServerRunning) {
                throw new CmplException("Model is not connected to a CmplServer");
            }

            if (_remoteStatus == CMPLSERVER_CLEANED) {
                throw new CmplException("Model was received and cleaned on the CmplServer");
            }

            if (_remoteStatus != PROBLEM_CANCELED) {
                Object[] ret = cmplServerExecute("knock", new Object[]{_jobId});
                _remoteStatus = (Integer) ret[0];
                if (_remoteStatus == CMPLSERVER_ERROR) {
                    cleanUp();
                    throw new CmplException((String) ret[1]);
                }
                handleOutput((String) ret[2]);
            }
        } else {
            throw new CmplException("Cmpl.knock can only be used in remote mode");

        }
    }

    /**
     * Cancels the Cmpl solving process on the connected CMPLServer
     * The status of the model can be obtained by the methods 
     * cmplStatus and cmplStatusText 
     * @throws CmplException 
     */
    public void cancel() throws CmplException {
        if (_remoteMode) {
            if (!_cmplServerRunning) {
                throw new CmplException("Model is not connected to a CmplServer");
            }

            if (_remoteStatus == CMPLSERVER_CLEANED) {
                throw new CmplException("Model has been received and cleaned on the CmplServer");
            }

            if (_remoteStatus != PROBLEM_CANCELED)  {
                Object[] ret = cmplServerExecute("cancel", new Object[]{_jobId});
                _remoteStatus = (Integer) ret[0];
                if (_remoteStatus == CMPLSERVER_ERROR) {
                    cleanUp();
                    throw new CmplException((String) ret[1]);
                }
                ret = cmplServerExecute("removeProblem", new Object[]{_jobId});
                _remoteStatus = (Integer) ret[0];
                if (_remoteStatus == CMPLSERVER_ERROR) {
                    cleanUp();
                    throw new CmplException((String) ret[1]);
                }
                _remoteStatus = CMPLSERVER_CLEANED;
            }
        } else {
           cleanUp();
        }
    }
    
    /**
     * Retrieves the Cmpl solution(s) if possible from the connected CMPLServer
     * last step of solving a model on a CMPLServer asynchronously 
     * @throws CmplException 
     * The status of the model and the solver can be obtained by the methods
     * cmplStatus, cmplStatusText,solverStatus and solverStatusText
     * @throws CmplException 
     */
    public void retrieve() throws CmplException {
        if (_remoteMode) {
            if (!_cmplServerRunning) {
                throw new CmplException("Model is not connected to a CmplServer");
            }

            if (_remoteStatus == CMPLSERVER_CLEANED) {
                throw new CmplException("Model was received and cleaned from the CmplServer");
            }

           
            if (_remoteStatus == CMPL_UNKNOWN) {
               knock();
            }

            if (_remoteStatus == PROBLEM_FINISHED) {
                Object[] ret = cmplServerExecute("getCmplMessages", new Object[]{_jobId});

                _remoteStatus = (Integer) ret[0];
                if (_remoteStatus != CMPLSERVER_ERROR) {
                    _status.readCmplMessages((String) ret[2]);
                } else {
                    cleanUp();
                    throw new CmplException((String) ret[1]);
                }

                if (_status.cmplStatus() ==  CMPL_FAILED) {
                    cleanUp();
                    throw new CmplException("Cmpl finished with errors" , _status.cmplMessageList() );
                }
               
                ret = cmplServerExecute("getSolutions", new Object[]{_jobId});
         
                _remoteStatus = (Integer)ret[0];
                
                if (_remoteStatus!= CMPLSERVER_ERROR) {
                    _solutionString = (String) ret[2];
                    _solutions.readSolution(_solutionString);
                } else {
                     cleanUp();   
                     throw new CmplException((String) ret[1]);
                }
                
                writeSolFiles();
                
                ret = cmplServerExecute("getCmplInfo", new Object[]{_jobId});
                _remoteStatus = (Integer)ret[0];
                
                if (_remoteStatus!= CMPLSERVER_ERROR) {
                    _cmplInfoString = (String) ret[2];
                    _cmplInfos.readCmplInfo(_cmplInfoString);
                } else {
                     cleanUp();   
                     throw new CmplException((String) ret[1]);
                }
                
                writeInfoFiles();
                
                cleanUp();
                
                
            } else {
                if (_remoteStatus == PROBLEM_CANCELED) {
                    throw new CmplException("Model has been canceled by user, cannot retrieve the solutions");
                } else {
                    throw new CmplException("Model is still running, cannot retrieve the solutions");
                }
            }
        } else {
            throw new CmplException("Cmpl.retrieve can only be used in remote mode");
        }
    }
    
  
    /**
     * Internal function to execute CMPLServer methods
     * @param methodName name of the CMPLServer method
     * @param params Parameter of CMPLServer methods
     * @return Array for several purposes
     * @throws CmplException 
     */
   // private static Object[] cmplServerExecute(String methodName, Object[] params) throws CmplException {
     private  Object[] cmplServerExecute(String methodName, Object[] params) throws CmplException {
       Object[] ret = null;
   
        int i=0;
    
        while (true)  {
            try {
                ret = (Object[]) _cmplServer.execute(methodName, params);
            } catch (Exception e) {
                i+=1;
                if (i>_maxCmplServerTries) {
                    //cleanUp();
                    throw new CmplException("CmplServer error : " + e);
                }
                continue;
            }
            break;
        }
        return ret;
    }
    
    /**
     * Saves the solution(s) as CmplSolutions file with filename
     * modelname.csol
     * @throws CmplException 
     */
    public void saveSolution() throws CmplException {
        saveSolution("");
    }

    /**
     * Saves the solution(s) as CmplSolutions file given filename
     * @param solFileName File name
     * @throws CmplException 
     */
    public void saveSolution(String solFileName) throws CmplException {
        String solFile;
        if (_solutions.nrOfSolutions() > 0) {
            if (solFileName.isEmpty()) {
                solFile = _problem+".csol";
            } else {
                solFile = solFileName;
            }
            
            if (!_remoteMode) {
                for ( String line : _solutions.solFileContent()) {
                    _solutionString+=line+"\n";
                }
            }
            _solutions.delSolFileContent();
            
            CmplTools.writeAsciiFile(solFile, _solutionString);

        } else {
            throw new CmplException("No solution found so far");
        }
    }
    
    /**
     * Saves the solution(s) as ASCII file with filename
     * modelname.csol
     * @throws CmplException 
     */
    public void saveSolutionAscii() throws CmplException {
        saveSolutionAscii("");
    }

    /**
     * Saves the solution(s) as ASCII file with given file name
     * @param solFileName File name
     * @throws CmplException 
     */
    public void saveSolutionAscii(String solFileName) throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            String solFile;
            if (solFileName.isEmpty()) {
                solFile = _problem + ".sol";
            } else {
                solFile = solFileName;
            }
            solutionReport(solFile);
        } else {
            throw new CmplException("No solution found so far");
        }
    }

    /**
     * Writes a standard solution report to stdOut
     * @throws CmplException 
     */
    public void solutionReport() throws CmplException {
        solutionReport("");
    }
    
    /**
     * Writes a standard solution report to a file 
     * Used by saveSolutionAscii
     * @param fileName File name
     * @throws CmplException 
     */
    public void solutionReport(String fileName) throws CmplException
    {
        String repStr = "";
        File file = new File(_model);
        
        
        if(_solutions.nrOfSolutions() > 0)
        {
            repStr += "---------------------------------------------------------------------------------------------------------\n";
            repStr += String.format("%-20s %s%n", "Problem", file.getName());
            repStr += String.format("%-20s %d%n", "Nr. of variables", _solutions.nrOfVariables());
            repStr += String.format("%-20s %d%n", "Nr. of constraints", _solutions.nrOfConstraints());
            repStr += String.format("%-20s %s%n", "Objective name", _solutions.objectiveName());
            
            if(_solutions.nrOfSolutions() > 1) {
                repStr += String.format("%-20s %d%n", "Nr. of solutions", _solutions.nrOfSolutions());
            }
            repStr += String.format("%-20s %s%n", "Solver name", _solutions.solver());
            repStr += String.format("%-20s %s%n", "Display variables", _solutions.varDisplayOptions());
            repStr += String.format("%-20s %s%n", "Display vonstraints", _solutions.conDisplayOptions());
            repStr += "---------------------------------------------------------------------------------------------------------\n";
            
            for ( CmplSolution s : _solutions.solutions())    
            {
                repStr += "\n";
                if (_solutions.nrOfSolutions() > 1) {
                    repStr += String.format("%-20s %d%n", "Solution nr.", s.idx());
                }
                repStr += String.format("%-20s %s%n","Objective status", s.status());
                repStr += String.format("%-20s %-20.2f (%s!)%n", "Objective value", s.value(), _solutions.objectiveSense());
                repStr +="\n";
                if(s.variables().size() > 0)
                {
                    repStr += String.format("%-20s%n", "Variables");
                    repStr += String.format("%-20s%5s%20s%20s%20s%20s%n","Name" , "Type" , "Activity", "LowerBound", "UpperBound" , "Marginal");
                    repStr += "---------------------------------------------------------------------------------------------------------\n";

                    for (CmplSolElement v : s.variables()) {
                        if (v.type().equals("C")) {
                            repStr += String.format("%-20s%5s%20.2f%20.2f%20.2f", v.name(), v.type(), (Double) v.activity(), v.lowerBound(), v.upperBound());
                        } else {
                            repStr += String.format("%-20s%5s%20d%20.2f%20.2f", v.name(), v.type(), (Long) v.activity(), v.lowerBound(), v.upperBound());
                        }
                        if (_solutions.isIntegerProgram()) {
                            repStr += String.format("%20s%n", "-");
                        } else {
                            repStr += String.format("%20.2f%n", v.marginal());
                        }
                        
                    }
                    repStr += "---------------------------------------------------------------------------------------------------------\n";
                }
                if(s.constraints().size() > 0)
                {
                    repStr += "\n";
                    repStr += String.format("%-20s%n", "Constraints");
                    repStr += String.format("%-20s%5s%20s%20s%20s%20s%n","Name" , "Type" , "Activity", "LowerBound", "UpperBound" , "Marginal");
                    repStr += "---------------------------------------------------------------------------------------------------------\n";
                    
                    for (CmplSolElement c : s.constraints()) {
                        repStr += String.format("%-20s%5s%20.2f%20.2f%20.2f", c.name(), c.type(), c.activity(), c.lowerBound(), c.upperBound());
                        if (_solutions.isIntegerProgram()) {
                            repStr += String.format("%20s%n", "-");
                        } else {
                            repStr += String.format("%20.2f%n",  c.marginal());
                        }
                        
                    }
                    repStr += "---------------------------------------------------------------------------------------------------------\n";
                }
                
            }
            if (fileName.isEmpty()) {
                System.out.print(repStr);
            } else {
                try {

                    BufferedWriter out = new BufferedWriter(new FileWriter(fileName));
                    out.write(repStr);
                    out.close();
                } catch (IOException e) {
                    throw new CmplException("IO error for file " + fileName + ": "+e);
                }
            }
        } else {
            throw new CmplException("No solution found so far");
        }
    }
    
    /**
     * Saves the solution(s) as CSV file with file name
     * modelname.csv
     * @throws CmplException 
     */
    public void saveSolutionCsv() throws CmplException {
        saveSolutionCsv("");
    }
   
    /**
     * Saves the solution(s) as CSV file with given file name
     * @param solFileName File name
     * @throws CmplException 
     */
    public void saveSolutionCsv(String solFileName) throws CmplException {
        if (_solutions.nrOfSolutions() > 0) {
            String solFile;
            if (solFileName.isEmpty()) {
                solFile = _problem + ".csv";
            } else {
                solFile = solFileName;
            }

            try {
                BufferedWriter out = new BufferedWriter(new FileWriter(solFile));

                out.write("CMPL csv export\n");
                out.write("\n");
                out.write(String.format("%s;%s\n", "Problem", _problem+".cmpl"));
                out.write(String.format("%s;%d\n", "Nr. of variables", _solutions.nrOfVariables()));
                out.write(String.format("%s;%d\n", "Nr. of constraints", _solutions.nrOfConstraints()));
                out.write(String.format("%s;%s\n", "Objective name", _solutions.objectiveName()));
                if (_solutions.nrOfSolutions() > 1) {
                    out.write(String.format("%s;%d\n", "Nr. of solutions", _solutions.nrOfSolutions()));
                }
                out.write(String.format("%s;%s\n", "Solver name", _solutions.solver()));
                out.write(String.format("%s;%s\n", "Display variables", _solutions.varDisplayOptions()));
                out.write(String.format("%s;%s\n", "Display constraints", _solutions.conDisplayOptions()));
       
                for (CmplSolution s : _solutions.solutions()) {
                    out.write("\n");
                    if (_solutions.nrOfSolutions() > 1) {
                        out.write(String.format("%s;%d\n", "Solution Nr.", s.idx() + 1));
                    }
                    out.write(String.format("%s;%s\n", "Objective status", s.status()));
                    out.write(String.format("%s;%f;(%s!)\n", "Objective value", s.value(), _solutions.objectiveSense()));

                    if (s.variables().size() > 0) {
                        out.write(String.format("%s\n", "Variables"));
                        out.write(String.format("%s;%s;%s;%s;%s;%s\n", "Name", "Type", "Activity", "LowerBound", "UpperBound", "Marginal"));
                        for (CmplSolElement v : s.variables()) {
                            if (v.type().equals("C")) {
                                out.write(String.format("%s;%s;%f;%f;%f", v.name(), v.type(), (Double) v.activity(), v.lowerBound(), v.upperBound() ));
                            } else {
                                out.write(String.format("%s;%s;%d;%f;%f", v.name(), v.type(), (Long) v.activity(), v.lowerBound(), v.upperBound() ));
                            }
                            if (_solutions.isIntegerProgram()) {
                                out.write(";-\n");
                            } else {
                                out.write(String.format(";%f\n", v.marginal()));
                            }

                        }
                    }
                 
                    if (s.constraints().size() > 0) {
                        out.write(String.format("%s\n", "Constraints"));
                        out.write(String.format("%s;%s;%s;%s;%s;%s\n", "Name", "Type", "Activity", "LowerBound", "UpperBound", "Marginal"));
                        for (CmplSolElement c : s.constraints()) {
                            out.write(String.format("%s;%s;%f;%f;%f", c.name(), c.type(), c.activity(), c.lowerBound(), c.upperBound()));
                            if (_solutions.isIntegerProgram()) {
                                out.write(";-\n");
                            } else {
                                out.write(String.format(";%f\n", c.marginal()));
                            }
                        }
                    }
                }

                out.close();
            } catch (Exception e) {
                throw new CmplException("IO error for file " + solFileName + ": " + e);
            }
        } else {
            throw new CmplException("No solution found so far");
        }
    }
    
   
   /**
     * Saves the solution(s) - controlled by the command line args -solution
     * -solutionAscii and -solutionCsv
     *
     * @throws CmplException
     */
    private void writeSolFiles() throws CmplException {
        String fName;
        handleOutput("\n");

        if (!cmplSolFile().isEmpty()) {
            if (cmplSolFile().equals("cmplStandard")) {
                fName = _problem + ".csol";
            } else {
                fName = cmplSolFile();
            }
            saveSolution(fName);
        }
        if (!asciiSolFile().isEmpty()) {
            if (asciiSolFile().equals("cmplStandard")) {
                fName = _problem + ".sol";
            } else {
                fName = asciiSolFile();
            }
            saveSolutionAscii(fName);
        }
        if (!csvSolFile().isEmpty()) {
            if (csvSolFile().equals("cmplStandard")) {
                fName = _problem + ".csv";
            } else {
                fName = csvSolFile();
            }
            saveSolutionCsv(fName);
        }
    }
  
    /**
     * Writes several statistics to files or stdout - controlled by the command
     * line arguments -matrix , -s and -l
     *
     * @throws CmplException
     */
    private void writeInfoFiles() throws CmplException {
        handleOutput("\n");

        if (!_cmplInfos.statisticsFileName().isEmpty()) {
            if (_cmplInfos.statisticsFileName().equals("stdOut")) {
                handleOutput(_cmplInfos.statisticsText());
                handleOutput("\n");
            } else {
                CmplTools.writeAsciiFile(_cmplInfos.statisticsFileName(), _cmplInfos.statisticsText());
                handleOutput("Statistics written to file: " + _cmplInfos.statisticsFileName());
            }
        }

        if (!_cmplInfos.varProdFileName().isEmpty()) {
            if (_cmplInfos.varProdFileName().equals("stdOut")) {
                handleOutput(_cmplInfos.varProdText());
                handleOutput("\n");
            } else {
                CmplTools.writeAsciiFile(_cmplInfos.varProdFileName(), _cmplInfos.varProdText());
                handleOutput("Variable products statistics written to file: " + _cmplInfos.varProdFileName());
            }
        }

        if (!_cmplInfos.matrixFileName().isEmpty()) {
            if (_cmplInfos.matrixFileName().equals("stdOut")) {
                handleOutput(_cmplInfos.matrixText());
                handleOutput("\n");
            } else {
                CmplTools.writeAsciiFile(_cmplInfos.matrixFileName(), _cmplInfos.matrixText());
                handleOutput("Generated matrix written to file: " + _cmplInfos.matrixFileName());
            }
        }
    }
    
    

    /**
     * Used for model.start() if the model is executed in parallel
     */    
    @Override
    public void run() 
    {
        try {     
            solve();
        } catch (CmplException ex) {
            Logger.getLogger(Cmpl.class.getName()).log(Level.SEVERE, null, ex);
        }
       
    }
    /**
     * Used for stopping a Cmpl thread
     */
    @Override
    public void interrupt() {
        _runCanceled=true;
        _cmplProc.destroy();
        try {
            cleanUp();
        } catch (CmplException ex) {
            Logger.getLogger(Cmpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

  
    
    
   
    
}




    