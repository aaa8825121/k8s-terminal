package com.kube.dto;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.Exec;
import org.apache.log4j.Logger;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class WsConnection extends  Thread {

    private InputStream inputStream ;
    private OutputStream outputStream;
    private Exec exec;
    private WebSocketSession session;
    private Pod pod;
    private ConsoleSize consoleSize;
    private Process proc;

    private static Logger logger = Logger.getLogger(WsConnection.class);

    public WsConnection(Map<String, String> stringStringMap, WebSocketSession session) {
        this.setSession(session);
        this.setExec( new Exec());
        this.setPod(new Pod(stringStringMap.get("name"),stringStringMap.get("namespace"),stringStringMap.get("container")));
        this.setConsoleSize(new ConsoleSize(stringStringMap.get("cols"),stringStringMap.get("rows")));
    }

    @Override
    public void run() {
        List<String> cmds = Arrays.asList( new String[] {"/bin/bash","/bin/sh"});
        cmds.forEach((s) -> startProcess(s));

    }

    private void startProcess(String shellPath){
        String namespace = this.getPod().getNamespace();
        String name = this.getPod().getName();
        String container = this.getPod().getContainer();
        Boolean tty = true;
        Boolean initValid = true;
        try {
            proc = exec.exec(namespace,name,new String[]{shellPath},container,true,tty);
            outputStream = proc.getOutputStream();
            inputStream = proc.getInputStream();
            try {
                while (true){
                    byte data[] = new byte[1024];
                    if (inputStream.read(data) != -1) {
                        TextMessage textMessage = new TextMessage(data);
                        if (initValid && isValidBash(textMessage,shellPath)){
                            break;
                        } else {
                            initValid = false;
                        }
                        session.sendMessage(textMessage);
                    }
                }
            } catch ( IOException e) {
                logger.warn("Pipe closed");
            } finally {
                proc.destroy();
                logger.info("session closed... exit thread");
            }


        } catch (ApiException | IOException e) {
            e.printStackTrace();
            try {
                logger.info("ApiException or IOException... close session");
                session.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }


    }

    //验证shell
    private boolean isValidBash(TextMessage textMessage,String shellPath){
        String failMessage = "OCI runtime exec failed";
        if (textMessage.getPayload().trim().indexOf(failMessage) != -1){
            logger.warn("OCI runtime exec failed: " + shellPath);
            return true;
        }else {
            return  false;
        }
    }

    //保险起见，最后关闭数据流
    protected void finalize() {
        try {
            outputStream.close();
            inputStream.close();
            outputStream = null;
            inputStream = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //退出 Process
    public void exit() {
        proc.destroyForcibly();
        try {
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void setOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    public Exec getExec() {
        return exec;
    }

    public void setExec(Exec exec) {
        this.exec = exec;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public void setSession(WebSocketSession session) {
        this.session = session;
    }


    public Pod getPod() {
        return pod;
    }

    public void setPod(Pod pod) {
        this.pod = pod;
    }

    public ConsoleSize getConsoleSize() {
        return consoleSize;
    }

    public void setConsoleSize(ConsoleSize consoleSize) {
        this.consoleSize = consoleSize;
    }



}
