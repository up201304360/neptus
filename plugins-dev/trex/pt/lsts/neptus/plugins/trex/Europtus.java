/*
 * Copyright (c) 2004-2015 Universidade do Porto - Faculdade de Engenharia
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
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENSE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://www.lsts.pt/neptus/licence.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: zp
 * Jun 17, 2015
 */
package pt.lsts.neptus.plugins.trex;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Vector;

import javax.swing.JPopupMenu;

import pt.lsts.imc.EntityParameter;
import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.IMCOutputStream;
import pt.lsts.imc.SetEntityParameters;
import pt.lsts.imc.TrexToken;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.iridium.DuneIridiumMessenger;
import pt.lsts.neptus.comm.iridium.HubIridiumMessenger;
import pt.lsts.neptus.comm.iridium.ImcIridiumMessage;
import pt.lsts.neptus.comm.iridium.IridiumManager;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.comm.manager.imc.ImcSystem;
import pt.lsts.neptus.comm.manager.imc.ImcSystemsHolder;
import pt.lsts.neptus.comm.manager.imc.MessageDeliveryListener;
import pt.lsts.neptus.comm.transports.DeliveryListener;
import pt.lsts.neptus.comm.transports.udp.UDPTransport;
import pt.lsts.neptus.console.ConsoleInteraction;
import pt.lsts.neptus.mp.Maneuver.SPEED_UNITS;
import pt.lsts.neptus.mp.templates.PlanCreator;
import pt.lsts.neptus.plugins.NeptusProperty;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.PluginUtils;
import pt.lsts.neptus.plugins.trex.goals.AUVDrifterSurvey;
import pt.lsts.neptus.renderer2d.StateRenderer2D;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.types.mission.plan.PlanType;
import pt.lsts.neptus.types.vehicle.VehicleType;
import pt.lsts.neptus.types.vehicle.VehiclesHolder;
import pt.lsts.neptus.util.GuiUtils;

import com.google.common.eventbus.Subscribe;

/**
 * @author zp
 */
@PluginDescription(name="Europtus Interface")
public class Europtus extends ConsoleInteraction implements MessageDeliveryListener, DeliveryListener {

    @NeptusProperty(category="Real Vehicles", name="First AUV (smaller surveys)")
    public String auv1 = "lauv-xplore-1";

    @NeptusProperty(category="Real Vehicles", name="Second AUV (bigger surveys)")
    public String auv2 = "lauv-xplore-2";

    @NeptusProperty(category="Europtus", name="Host and Port for local europtus server")
    public String europtus = "127.0.0.1:8800";

    @NeptusProperty(category="Simulated Vehicles", name="Host and Port for First Simulator")
    public String sim_auv1 = "127.0.0.1:6002";

    @NeptusProperty(category="Simulated Vehicles",name="Host and Port for Second Simulator")
    public String sim_auv2 = "127.0.0.1:6003";
    
    @NeptusProperty(category="Survey Parameters", name="Smaller survey side length, in meters")
    public double survey_size = 300;

    @NeptusProperty(category="Survey Parameters", name="Ground Speed, in meters")
    public double ground_speed = 1.25;
    
    @NeptusProperty(category="Survey Parameters", name="Time spent at surface during popups, in seconds")
    public int popup_secs = 30;
    
    @NeptusProperty(category="Survey Parameters", name="Path rotation, in degrees")
    public double rotation = 0;
    
    @NeptusProperty(category="Survey Parameters", name="Maximum depth, in meters")
    public double max_depth = 20;    
    
    @NeptusProperty(category="Real Vehicles", name="Connection to be used to send Goals")
    public Connection connection_type = Connection.IMC;
    
    private String europtus_host = null, auv1_host = null, auv2_host = null;
    private int europtus_port = -1, auv1_port = -1, auv2_port = -1;

    private PlanType plan1 = null, plan2 = null;
    
    private HubIridiumMessenger hubMessenger = null;
    private DuneIridiumMessenger duneMessenger = null;
    private UDPTransport imcTransport = null;
    
    
    @Subscribe
    public void on(TrexToken token) {
        
    }
    
    public enum Connection {
        MantaIridium,
        HubIridium,
        IMC
    }

    
    // send to europtus server
    void sendToEuroptus(TrexToken msg) throws Exception {
        if (europtus_host == null)
            throw new Exception("Europtus host and port not correctly set.");
        sendUdp(msg, europtus_host, europtus_port);
    }

    // send to udp destination
    void sendUdp(IMCMessage msg, String host, int port) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        msg.serialize(new IMCOutputStream(baos));
        getUdpTransport().sendMessage(host, port, baos.toByteArray(), this);
    }

    Collection<ImcIridiumMessage> wrap(IMCMessage msg) throws Exception {
        msg.setSrc(ImcMsgManager.getManager().getLocalId().intValue());
        return IridiumManager.iridiumEncode(msg);
    }

    void sendToVehicle(String vehicle, IMCMessage msg) throws Exception {
        ImcSystem sys = ImcSystemsHolder.getSystemWithName(vehicle);
        if (sys != null)
            msg.setDst(sys.getId().intValue());
        else {
            VehicleType vt = VehiclesHolder.getVehicleById(vehicle);
            if (vt != null)
                msg.setDst(vt.getImcId().intValue());
            else
                throw new Exception("Vehicle "+vehicle+" is unknown.");
        }
        
        NeptusLog.pub().info("Send "+msg.getAbbrev()+" to "+vehicle+" ("+msg.getDst()+") using "+connection_type);
        switch (connection_type) {
            case IMC:
                ImcMsgManager.getManager().sendMessageToSystem(msg, vehicle, this);
                break;
            case HubIridium:
                for (ImcIridiumMessage m : wrap(msg))                
                    getHubMessenger().sendMessage(m);
                break;
            case MantaIridium:
                for (ImcIridiumMessage m : wrap(msg))                
                    getDuneMessenger().sendMessage(m);
                break;
            default:
                break;
        }
    }

    void sendToVehicle1(IMCMessage msg) throws Exception {
        sendToVehicle(auv1, msg);

        if (auv1_host != null)
            sendUdp(msg, auv1_host, auv1_port);
    }

    void sendToVehicle2(IMCMessage msg) throws Exception {
        sendToVehicle(auv2, msg);

        if (auv2_host != null)
            sendUdp(msg, auv2_host, auv2_port);        
    }

    @Override
    public void deliveryError(IMCMessage message, Object error) {
        if (error instanceof Throwable)
            deliveryResult(ResultEnum.Error, new Exception((Throwable)error));
        else
            deliveryResult(ResultEnum.Error, new Exception(""+error));
    }

    @Override
    public void deliverySuccess(IMCMessage message) {
        deliveryResult(ResultEnum.Success, null);
    }

    @Override
    public void deliveryTimeOut(IMCMessage message) {
        deliveryResult(ResultEnum.TimeOut, new Exception("Time out while sending "+message.getAbbrev()));
    }

    @Override
    public void deliveryUncertain(IMCMessage message, Object msg) {
        deliveryResult(ResultEnum.Success, null);
    }

    @Override
    public void deliveryUnreacheable(IMCMessage message) {
        deliveryResult(ResultEnum.Unreacheable, new Exception("Destination is unreacheable"));
    }

    @Override
    public void deliveryResult(ResultEnum result, Exception error) {
        NeptusLog.pub().info("Delivery result: "+result, error);
    }

    /**
     * @return the hubMessenger
     */
    public synchronized HubIridiumMessenger getHubMessenger() {
        if (hubMessenger == null)
            hubMessenger = new HubIridiumMessenger();
        return hubMessenger;
    }

    /**
     * @return the duneMessenger
     */
    public synchronized DuneIridiumMessenger getDuneMessenger() {
        if (duneMessenger == null)
            duneMessenger = new DuneIridiumMessenger();
        return duneMessenger;
    }

    public synchronized UDPTransport getUdpTransport() {
        if (imcTransport == null)
            imcTransport = new UDPTransport();
        return imcTransport;
    }

    @Override
    public void propertiesChanged() {
        try {
            europtus_host = europtus.split("\\:")[0];
            europtus_port = Integer.parseInt(europtus.split("\\:")[1]);
        }
        catch (Exception e) {
            NeptusLog.pub().error("Error parsing Europtus host:port", e);
            europtus_host = null;
            europtus_port = -1;
        }

        try {
            auv2_host = sim_auv2.split(":")[0];
            auv2_port = Integer.parseInt(sim_auv2.split(":")[1]);
        }
        catch (Exception e) {
            NeptusLog.pub().error("Error parsing AUV2 simulator host:port", e);
            auv2_host = null;
            auv2_port = -1;
        }

        try {
            auv1_host = sim_auv1.split(":")[0];
            auv1_port = Integer.parseInt(sim_auv1.split(":")[1]);
        }
        catch (Exception e) {
            NeptusLog.pub().error("Error parsing AUV1 simulator host:port", e);
            auv1_host = null;
            auv1_port = -1;
        }
    }

    @Override
    public void mouseClicked(MouseEvent event, StateRenderer2D source) {
        JPopupMenu popup = new JPopupMenu();
        final LocationType loc = source.getRealWorldLocation(event.getPoint());

        popup.add("Enable TREX").addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                SetEntityParameters setParams = new SetEntityParameters();
                setParams.setName("TREX");
                EntityParameter param = new EntityParameter("Active", "false");
                Vector<EntityParameter> p = new Vector<>();
                p.add(param);
                setParams.setParams(p);
                try {
                    sendToVehicle1(setParams.cloneMessage());
                    sendToVehicle2(setParams.cloneMessage());
                }
                catch (Exception ex) {
                    NeptusLog.pub().error(
                            ex.getClass().getSimpleName() + " while enabling T-REX: " + ex.getMessage(), ex);
                    GuiUtils.errorMessage(getConsole(), "Error enabling T-REX",
                            ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            }
        });

        popup.add("Disable TREX").addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                SetEntityParameters setParams = new SetEntityParameters();
                setParams.setName("TREX");
                EntityParameter param = new EntityParameter("Active", "true");
                Vector<EntityParameter> p = new Vector<>();
                p.add(param);
                setParams.setParams(p);
                
                try {
                    sendToVehicle1(setParams.cloneMessage());
                    sendToVehicle2(setParams.cloneMessage());
                }
                catch (Exception ex) {
                    NeptusLog.pub().error(
                            ex.getClass().getSimpleName() + " while disabling T-REX: " + ex.getMessage(), ex);
                    GuiUtils.errorMessage(getConsole(), "Error disabling T-REX",
                            ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
            }
        });
        
        popup.addSeparator();
        popup.add("Survey around here").addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                AUVDrifterSurvey survey1 = new AUVDrifterSurvey(loc.getLatitudeRads(), loc.getLongitudeRads(),
                        (float) survey_size, 0f, false, AUVDrifterSurvey.PathType.SQUARE_TWICE, (float) Math
                                .toRadians(rotation));

                AUVDrifterSurvey survey2 = new AUVDrifterSurvey(loc.getLatitudeRads(), loc.getLongitudeRads(),
                        (float) (survey_size * 2), 0f, false, AUVDrifterSurvey.PathType.SQUARE, (float) Math
                                .toRadians(rotation));

                plan1 = asNeptusPlan(survey1);
                plan1.setVehicle(auv1);
                plan1.setId("trex_"+auv1);
                
                plan2 = asNeptusPlan(survey2);
                plan2.setVehicle(auv2);
                plan2.setId("trex_"+auv2);
                
                getConsole().getMission().addPlan(plan1);
                getConsole().getMission().addPlan(plan2);
                getConsole().getMission().save(false);
                getConsole().warnMissionListeners();
                
                try {
                    sendToVehicle1(survey1.asIMCMsg());
                    sendToVehicle2(survey2.asIMCMsg());                
                }
                catch (Exception ex) {
                    NeptusLog.pub().error(
                            ex.getClass().getSimpleName() + " while sending survey command: " + ex.getMessage(), ex);
                    GuiUtils.errorMessage(getConsole(), "Error sending survey command",
                            ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }   
            }
        });
        
        popup.addSeparator();
        popup.add("Plug-in settings").addActionListener(new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e) {
                PluginUtils.editPluginProperties(Europtus.this, true);
            }
        });
        
        popup.show(event.getComponent(), event.getX(), event.getY());
    }
    
    private PlanType asNeptusPlan(AUVDrifterSurvey survey) {
        
        PlanCreator pc = new PlanCreator(getConsole().getMission());
        pc.setSpeed(1.25, SPEED_UNITS.METERS_PS);
        pc.setLocation(survey.getLocation());
        AffineTransform transform = new AffineTransform();
        transform.rotate(survey.getRotationRads());
        PathIterator it = survey.getShape().getPathIterator(transform);
        double[] coords = new double[6];        
        
        while(!it.isDone()) {
            it.currentSegment(coords);
            LocationType loc = new LocationType(survey.getLocation());
            loc.translatePosition(-coords[1], coords[0], 0);
            loc.convertToAbsoluteLatLonDepth();
            pc.setLocation(loc);
            pc.setDepth(max_depth/2);
            pc.addManeuver("YoYo", "amplitude", max_depth/2 - 1, "pitchAngle", Math.toRadians(15));
            pc.setDepth(0);
            pc.addManeuver("Elevator");
            pc.addManeuver("StationKeeping", "duration", 30);
            it.next();
        }
        pc.addManeuver("StationKeeping", "duration", 0);
        
        return pc.getPlan();
    }


    @Override
    public void initInteraction() {
        System.out.println("Props changed");
        propertiesChanged();
    }

    @Override
    public void cleanInteraction() {

    }
}
