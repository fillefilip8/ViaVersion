package us.myles.ViaVersion.transformers;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import net.minecraft.server.v1_8_R3.PacketDataSerializer;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import us.myles.ViaVersion.CancelException;
import us.myles.ViaVersion.ConnectionInfo;
import us.myles.ViaVersion.Core;
import us.myles.ViaVersion.PacketUtil;
import us.myles.ViaVersion.handlers.ViaVersionInitializer;
import us.myles.ViaVersion.packets.PacketType;
import us.myles.ViaVersion.packets.State;

public class IncomingTransformer {
    private static Gson gson = new Gson();
    private final Channel channel;
    private final ConnectionInfo info;
    private final ViaVersionInitializer init;

    public IncomingTransformer(Channel channel, ConnectionInfo info, ViaVersionInitializer init) {
        this.channel = channel;
        this.info = info;
        this.init = init;
    }

    public void transform(int packetID, ByteBuf input, ByteBuf output) throws CancelException {
        PacketType packet = PacketType.getIncomingPacket(info.getState(), packetID);
        if (packet == null) {
            System.out.println("incoming packet not found " + packetID + " state: " + info.getState());
            throw new RuntimeException("Incoming Packet not found? " + packetID + " State: " + info.getState() + " Version: " + info.getProtocol());
        }
        int original = packetID;

        if (packet.getPacketID() != -1) {
            packetID = packet.getPacketID();
        }
        if (packet != PacketType.PLAY_PLAYER_POSITION_LOOK_REQUEST && packet != PacketType.PLAY_KEEP_ALIVE_REQUEST && packet != PacketType.PLAY_PLAYER_POSITION_REQUEST && packet != PacketType.PLAY_PLAYER_LOOK_REQUEST) {
            System.out.println("Packet Type: " + packet + " New ID: " + packetID + " Original: " + original);
        }
        if (packet == PacketType.PLAY_TP_CONFIRM) {
            System.out.println("Cancelling TP Confirm");
            throw new CancelException();
        }
        PacketUtil.writeVarInt(packetID, output);
        if (packet == PacketType.HANDSHAKE) {
            System.out.println("Readable Bytes: " + input.readableBytes());
            int protVer = PacketUtil.readVarInt(input);
            info.setProtocol(protVer);
            PacketUtil.writeVarInt(protVer <= 102 ? protVer : 47, output); // pretend to be older

            System.out.println("Incoming prot ver: " + protVer);
            if (protVer <= 102) {
                // Not 1.9 remove pipes
                this.init.remove();
            }
            String serverAddress = PacketUtil.readString(input);
            PacketUtil.writeString(serverAddress, output);

            int serverPort = input.readUnsignedShort();
            output.writeShort(serverPort);

            int nextState = PacketUtil.readVarInt(input);
            PacketUtil.writeVarInt(nextState, output);

            if (nextState == 1) {
                info.setState(State.STATUS);
            }
            if (nextState == 2) {
                info.setState(State.LOGIN);
            }
            return;
        }
        if(packet == PacketType.PLAY_TAB_COMPLETE_REQUEST){
            String text = PacketUtil.readString(input);
            PacketUtil.writeString(text, output);
            input.readBoolean(); // assume command
            output.writeBytes(input);
            return;
        }
        if (packet == PacketType.PLAY_PLAYER_DIGGING) {
            byte status = input.readByte();
            if (status == 6) { // item swap
                throw new CancelException();
            }
            output.writeByte(status);
            // read position
            Long pos = input.readLong();
            output.writeLong(pos);
            short face = input.readUnsignedByte();
            output.writeByte(face);
            return;
        }
        if (packet == PacketType.PLAY_CLIENT_SETTINGS) {
            String locale = PacketUtil.readString(input);
            PacketUtil.writeString(locale, output);

            byte view = input.readByte();
            output.writeByte(view);

            int chatMode = PacketUtil.readVarInt(input);
            output.writeByte(chatMode);

            boolean chatColours = input.readBoolean();
            output.writeBoolean(chatColours);

            short skinParts = input.readUnsignedByte();
            output.writeByte(skinParts);

            int mainHand = PacketUtil.readVarInt(input);
            System.out.println("Main hand: " + mainHand);
            return;
        }
        if (packet == PacketType.PLAY_ANIMATION_REQUEST) {
            int hand = PacketUtil.readVarInt(input);
            System.out.println("Animation request " + hand);
            return;
        }
        if (packet == PacketType.PLAY_USE_ENTITY) {
            int target = PacketUtil.readVarInt(input);
            PacketUtil.writeVarInt(target, output);

            int type = PacketUtil.readVarInt(input);
            PacketUtil.writeVarInt(type, output);
            if (type == 2) {
                float targetX = input.readFloat();
                output.writeFloat(targetX);
                float targetY = input.readFloat();
                output.writeFloat(targetY);
                float targetZ = input.readFloat();
                output.writeFloat(targetZ);
            }
            if (type == 0 || type == 2) {
                int hand = PacketUtil.readVarInt(input); // lel
            }
            return;
        }
        if (packet == PacketType.PLAY_PLAYER_BLOCK_PLACEMENT) {
            Long position = input.readLong();
            output.writeLong(position);
            int face = PacketUtil.readVarInt(input);
            output.writeByte(face);
            int hand = PacketUtil.readVarInt(input);
            System.out.println("hand: " + hand);
            // write item in hand
            output.writeShort(-1);

            short curX = input.readUnsignedByte();
            output.writeByte(curX);
            short curY = input.readUnsignedByte();
            output.writeByte(curY);
            short curZ = input.readUnsignedByte();
            output.writeByte(curZ);
            return;
        }
        if (packet == PacketType.PLAY_USE_ITEM) {
            output.clear();
            PacketUtil.writeVarInt(PacketType.PLAY_PLAYER_BLOCK_PLACEMENT.getPacketID(), output);
            // Simulate using item :)
            output.writeLong(-1L);
            output.writeByte(-1);
            int hand = PacketUtil.readVarInt(input);
            System.out.println("hand: " + hand);
            // write item in hand
            output.writeShort(-1);

            output.writeByte(-1);
            output.writeByte(-1);
            output.writeByte(-1);
            return;
        }
        output.writeBytes(input);
    }
}
