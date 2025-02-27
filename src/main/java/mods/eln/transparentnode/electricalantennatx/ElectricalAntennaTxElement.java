package mods.eln.transparentnode.electricalantennatx;

import mods.eln.Eln;
import mods.eln.i18n.I18N;
import mods.eln.misc.Coordinate;
import mods.eln.misc.Direction;
import mods.eln.misc.LRDU;
import mods.eln.misc.Utils;
import mods.eln.node.NodeBase;
import mods.eln.node.NodeManager;
import mods.eln.node.transparent.TransparentNode;
import mods.eln.node.transparent.TransparentNodeDescriptor;
import mods.eln.node.transparent.TransparentNodeElement;
import mods.eln.sim.ElectricalLoad;
import mods.eln.sim.ThermalLoad;
import mods.eln.sim.mna.misc.MnaConst;
import mods.eln.sim.nbt.*;
import mods.eln.transparentnode.electricalantennarx.ElectricalAntennaRxElement;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.util.HashMap;
import java.util.Map;

public class ElectricalAntennaTxElement extends TransparentNodeElement {

    ElectricalAntennaTxSlowProcess slowProcess = new ElectricalAntennaTxSlowProcess(this);

    NbtElectricalLoad powerIn = new NbtElectricalLoad("powerIn");
    NbtElectricalGateInput commandIn = new NbtElectricalGateInput("commandIn");
    NbtElectricalGateOutput signalOut = new NbtElectricalGateOutput("signalOut");
    NbtElectricalGateOutputProcess signalOutProcess = new NbtElectricalGateOutputProcess("signalOutProcess", signalOut);

    NbtResistor powerResistor = new NbtResistor("powerResistor", powerIn, null);
    ElectricalAntennaTxElectricalProcess electricalProcess = new ElectricalAntennaTxElectricalProcess(this);

    LRDU rot = LRDU.Down;

    boolean placeBoot = true;

    ElectricalAntennaTxDescriptor descriptor;

    Coordinate rxCoord = null;
    ElectricalAntennaRxElement rxElement = null;
    double powerEfficency = 0.0;

    public ElectricalAntennaTxElement(TransparentNode transparentNode, TransparentNodeDescriptor descriptor) {
        super(transparentNode, descriptor);
        powerIn.setAsPrivate();

        slowProcessList.add(slowProcess);

        electricalLoadList.add(powerIn);
        electricalLoadList.add(commandIn);
        electricalLoadList.add(signalOut);
        electricalComponentList.add(signalOutProcess);
        electricalComponentList.add(powerResistor);
        electricalProcessList.add(electricalProcess);

        this.descriptor = (ElectricalAntennaTxDescriptor) descriptor;
    }

    public void txDisconnect() {
        ElectricalAntennaRxElement rx = getRxElement();

        if (rx != null) rx.rxDisconnect();
        rxCoord = null;
        rxElement = null;
    }

    ElectricalAntennaRxElement getRxElement() {
        if (rxCoord == null) return null;
        if (rxElement == null) {
            NodeBase node = NodeManager.instance.getNodeFromCoordonate(rxCoord);
            if (node != null && node instanceof TransparentNode && ((TransparentNode) node).element instanceof ElectricalAntennaRxElement)
                rxElement = (ElectricalAntennaRxElement) ((TransparentNode) node).element;
            else {
                rxCoord = null;
                Utils.println("ASSERT ElectricalAntennaRxElement getRxElement()");
            }
        }
        return rxElement;
    }

    @Nullable
    @Override
    public ElectricalLoad getElectricalLoad(Direction side, LRDU lrdu) {
        if (front.getInverse() != side.applyLRDU(lrdu)) return null;

        if (side == front.applyLRDU(rot)) return powerIn;
        if (side == front.applyLRDU(rot.left())) return signalOut;
        if (side == front.applyLRDU(rot.right())) return commandIn;
        return null;
    }

    @Nullable
    @Override
    public ThermalLoad getThermalLoad(@NotNull Direction side, @NotNull LRDU lrdu) {
        return null;
    }

    @Override
    public int getConnectionMask(Direction side, LRDU lrdu) {
        if (front.getInverse() != side.applyLRDU(lrdu)) return 0;

        if (side == front.applyLRDU(rot)) return NodeBase.maskElectricalPower;
        if (side == front.applyLRDU(rot.left())) return NodeBase.maskElectricalOutputGate;
        if (side == front.applyLRDU(rot.right())) return NodeBase.maskElectricalInputGate;
        return 0;
    }

    @NotNull
    @Override
    public String multiMeterString(@NotNull Direction side) {
        return "";
    }

    @NotNull
    @Override
    public String thermoMeterString(@NotNull Direction side) {
        return "";
    }

    void calculatePowerInRp() {
        double cmd = commandIn.getNormalized();
        if (cmd == 0.0)
            powerResistor.setR(MnaConst.highImpedance);
        else
            powerResistor.setR(descriptor.electricalNominalInputR / cmd);
    }

    @Override
    public void initialize() {
        descriptor.cable.applyTo(powerIn);
        calculatePowerInRp();
        connect();
    }

    @Override
    public boolean onBlockActivated(EntityPlayer player, Direction side, float vx, float vy, float vz) {
        if (Utils.isPlayerUsingWrench(player)) {
            rot = rot.getNextClockwise();
            node.reconnect();
            return true;
        }
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.getBoolean("rxCoordValid")) {
            rxCoord = new Coordinate();
            rxCoord.readFromNBT(nbt, "rxCoord");
        }
        rot = LRDU.readFromNBT(nbt, "rot");
        placeBoot = false;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (rxCoord == null)
            nbt.setBoolean("rxCoordValid", false);
        else {
            nbt.setBoolean("rxCoordValid", true);
            rxCoord.writeToNBT(nbt, "rxCoord");
        }
        rot.writeToNBT(nbt, "rot");
    }

    @Override
    public void onBreakElement() {
        txDisconnect();
        super.onBreakElement();
    }

    public boolean mustHaveFloor() {
        return false;
    }

    public boolean mustHaveCeiling() {
        return false;
    }

    public boolean mustHaveWall() {
        return false;
    }

    public boolean mustHaveWallFrontInverse() {
        return true;
    }

    @Override
    public void networkSerialize(DataOutputStream stream) {
        super.networkSerialize(stream);
        rot.serialize(stream);
        node.lrduCubeMask.getTranslate(front.getInverse()).serialize(stream);
    }

    @NotNull
    @Override
    public Map<String, String> getWaila() {
        Map<String, String> info = new HashMap<String, String>();
        info.put(I18N.tr("Transmitting"), commandIn.getNormalized() > 0 ? "Yes" : "No");
        info.put(I18N.tr("Efficiency"), Utils.plotPercent("", powerEfficency));
        if (Eln.wailaEasyMode) {
            info.put(I18N.tr("Power"), Utils.plotPower("", powerIn.getI() * powerIn.getU()));
        }
        return info;
    }
}
