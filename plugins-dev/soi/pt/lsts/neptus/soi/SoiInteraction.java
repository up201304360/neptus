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
 * 09/10/2017
 */
package pt.lsts.neptus.soi;

import java.util.LinkedHashMap;

import com.google.common.eventbus.Subscribe;

import pt.lsts.imc.SoiCommand;
import pt.lsts.imc.SoiCommand.COMMAND;
import pt.lsts.imc.SoiCommand.TYPE;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.comm.iridium.ImcIridiumMessage;
import pt.lsts.neptus.comm.iridium.IridiumManager;
import pt.lsts.neptus.comm.manager.imc.ImcMsgManager;
import pt.lsts.neptus.comm.manager.imc.ImcSystemsHolder;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.notifications.Notification;
import pt.lsts.neptus.i18n.I18n;
import pt.lsts.neptus.plugins.NeptusMenuItem;
import pt.lsts.neptus.plugins.NeptusProperty;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.PluginUtils;
import pt.lsts.neptus.plugins.SimpleRendererInteraction;
import pt.lsts.neptus.util.GuiUtils;

/**
 * @author zp
 *
 */
@PluginDescription(name = "SoiInteraction")
public class SoiInteraction extends SimpleRendererInteraction {

    private static final long serialVersionUID = 477322168507708457L;
    private LinkedHashMap<String, SoiSettings> settings = new LinkedHashMap<>();
    
    @NeptusProperty(name = "Communication Mean", description = "Communication mean to use to send commands")
    public CommMean commMean = CommMean.WiFi;

    @NeptusMenuItem("Tools>SOI>Send Resume")
    public void sendResume() {
        SoiCommand cmd = new SoiCommand();
        cmd.setCommand(COMMAND.RESUME);
        cmd.setType(TYPE.REQUEST);
        sendCommand(cmd);
    }

    @NeptusMenuItem("Tools>SOI>Send Stop")
    public void sendStop() {
        SoiCommand cmd = new SoiCommand();
        cmd.setCommand(COMMAND.STOP);
        cmd.setType(TYPE.REQUEST);
        sendCommand(cmd);
    }

    @NeptusMenuItem("Tools>SOI>Send Plan")
    public void sendPlan() {
        // FIXME
        GuiUtils.errorMessage(getConsole(), "Not implemented",
                "Not yet implemented. Send a plan named 'soi_plan' instead.");
    }
    
    @NeptusMenuItem("Tools>SOI>Clear Plan")
    public void clearPlan() {
        SoiCommand cmd = new SoiCommand();
        cmd.setCommand(COMMAND.EXEC);
        cmd.setType(TYPE.REQUEST);
    }
    
    @NeptusMenuItem("Tools>SOI>Settings")
    public void sendSettings() {
        if (PluginUtils.editPluginProperties(settings, getConsole(), true))
            return;
        
        //TODO
    }
    
    
   
    @Subscribe
    public void on(SoiCommand cmd) {
        
        if (cmd.getType() != SoiCommand.TYPE.SUCCESS)
            return;
        
        switch (cmd.getCommand()) {
            case GET_PARAMS:
                LinkedHashMap<String, String> params = cmd.getSettings();
                //TODO: set params
                break;

            default:
                break;
        }
    }

    private void sendCommand(SoiCommand cmd) {

        if (commMean == CommMean.WiFi) {
            send(cmd);
            getConsole().post(Notification.success(I18n.text("Command sent"),
                    I18n.textf("%cmd sent over UDP to %vehicle.", cmd.getCommandStr(), getConsole().getMainSystem())));
        }
        else if (commMean == CommMean.Iridium) {
            ImcIridiumMessage msg = new ImcIridiumMessage();
            msg.setSource(ImcMsgManager.getManager().getLocalId().intValue());

            try {
                msg.setDestination(ImcSystemsHolder.getSystemWithName(getConsole().getMainSystem()).getId().intValue());
                msg.setMsg(cmd);
                IridiumManager.getManager().send(msg);
                getConsole().post(Notification.success(I18n.text("Command sent"), I18n.textf(
                        "%cmd sent over Iridium to %vehicle.", cmd.getCommandStr(), getConsole().getMainSystem())));
            }
            catch (Exception e) {
                getConsole().post(Notification.error(I18n.textf("Error sending %cmd", cmd.getCommandStr()),
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
                NeptusLog.pub().error(e);
            }
        }
    }

    /**
     * @param console
     */
    public SoiInteraction(ConsoleLayout console) {
        super(console);
    }

    @Override
    public boolean isExclusive() {
        return true;
    }

    @Override
    public void cleanSubPanel() {

    }

    @Override
    public void initSubPanel() {

    }

    enum CommMean {
        WiFi,
        Iridium
    }

    class SoiSettings {
        @NeptusProperty(description = "Nominal Speed")
        double speed = 1;

        @NeptusProperty(description = "Maximum Depth")
        double max_depth = 10;

        @NeptusProperty(description = "Minimum Depth")
        double min_depth = 0.0;

        @NeptusProperty(description = "Maximum Speed")
        double max_speed = 1.5;

        @NeptusProperty(description = "Minimum Speed")
        double min_speed = 0.7;

        @NeptusProperty(description = "Maximum time underwater")
        int mins_under = 10;

        @NeptusProperty(description = "Number where to send reports")
        String sms_number = "+351914785889";
      
        @NeptusProperty(description = "Seconds to idle at each vertex")
        int wait_secs = 60;

        @NeptusProperty(description = "SOI plan identifier")
        String soi_plan_id = "soi_plan";

        @NeptusProperty(description = "Cyclic execution")
        boolean cycle = false;
    }
}
