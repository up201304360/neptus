/*
 * Copyright (c) 2004-2017 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * Modified European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the Modified EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://github.com/LSTS/neptus/blob/develop/LICENSE.md
 * and http://ec.europa.eu/idabc/eupl.html.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: zp
 * Nov 26, 2014
 */
package pt.lsts.neptus.plugins.pddl;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.TimeZone;
import java.util.Vector;

import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.manager.imc.ImcSystemsHolder;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.types.vehicle.VehicleType;
import pt.lsts.neptus.util.FileUtil;

/**
 * @author zp
 *
 */
public class MVProblemSpecification {

    public static final int constantSpeed = 1;
    public static final int powerUnitMultiplier = 1000;
    Vector<SamplePointTask> sampleTasks = new Vector<SamplePointTask>();
    Vector<SurveyAreaTask> surveyTasks = new Vector<SurveyAreaTask>();
    Vector<VehicleType> vehicles = new Vector<VehicleType>();
    LocationType defaultLoc = null;
    private String command_speed = "lpg -o DOMAIN -f INITIAL_STATE -out OUTFILE -speed";
    private String command_secs = "lpg -o DOMAIN -f INITIAL_STATE -out OUTFILE -n 10 -cputime ";
    
    private MVSolution solution;

    private MVDomainModel domainModel = MVDomainModel.V1;
    
    LinkedHashMap<String, LocationType> calculateLocations() {
        LinkedHashMap<String, LocationType> locations = new LinkedHashMap<String, LocationType>();

        // calculate all positions to be given to the planner, first from vehicles
        for (VehicleType v : vehicles) {
            LocationType depot = new LocationType(defaultLoc);
            try {
                depot = ImcSystemsHolder.getSystemWithName(v.getId()).getLocation();
            }
            catch (Exception e) {};

            locations.put(v.getNickname()+"_depot", depot);               
        }

        // and then tasks
        for (SurveyAreaTask task : surveyTasks) {
            locations.put(task.getName()+"_entry", task.getEntryPoint());
            locations.put(task.getName()+"_exit", task.getEndPoint());
        }
        for (SamplePointTask task : sampleTasks) {
            locations.put(task.getName()+"_oi", task.getLocation());            
        }

        return locations;
    }

    public String solve(int secs) throws Exception {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd-HHmmss");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = fmt.format(new Date());
        String input_file = "log/pddl/problem_"+timestamp+".pddl";
        String output_file = "log/pddl/solution_"+timestamp+".SOL";
        
        
        FileUtil.saveToFile(input_file, asPDDL());
        String cmd = command_secs+secs;
        if (secs == 0)
            cmd = command_speed;
        
        cmd = cmd.replaceAll("DOMAIN", domainModel.file().getAbsolutePath());
        cmd = cmd.replaceAll("INITIAL_STATE", "problem_"+timestamp+".pddl");
        cmd = cmd.replaceAll("OUTFILE", "solution_"+timestamp+".SOL");
        
        cmd = cmd.replaceAll("/", System.getProperty("file.separator"));
        Process p = Runtime.getRuntime().exec(cmd, null, new File("log/pddl"));
        StringBuilder result = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = reader.readLine();

        while (line != null) {
            result.append(line.toLowerCase().trim()+"\n");
            System.out.println(line);
            line = reader.readLine();
        }
        File outputFile = new File(output_file);
        if (outputFile.canRead()) {
            solution = domainModel.translator().parseSolution(this, FileUtil.getFileAsString(outputFile));
            return FileUtil.getFileAsString(outputFile);
        }        
        
        return "";        
    }


    public MVProblemSpecification(MVDomainModel model, Collection<VehicleType> vehicles, Collection<MVPlannerTask> tasks, LocationType defaultLoc) {

        this.domainModel = model;
        this.defaultLoc = defaultLoc;

        for (MVPlannerTask t : tasks) {
            if (t instanceof SurveyAreaTask) 
                surveyTasks.add((SurveyAreaTask)t);
            else if (t instanceof SamplePointTask) {
                sampleTasks.add((SamplePointTask)t);
            }
        }

        this.vehicles.addAll(vehicles);
    }

    public String asPDDL() {
        return domainModel.translator().getInitialState(this);
    }
    
    public MVSolution getSolution() {
        return solution;
    }

    
}
