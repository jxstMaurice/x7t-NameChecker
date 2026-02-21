package com.x7t.namechecker;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;

public class TextEventCompat {
    
    private static final boolean USE_NEW_API;
    
    private static Object CLICK_ACTION_OPEN_URL;
    private static Object CLICK_ACTION_RUN_COMMAND;
    private static Object CLICK_ACTION_SUGGEST_COMMAND;
    private static Object CLICK_ACTION_COPY_TO_CLIPBOARD;
    private static Object HOVER_ACTION_SHOW_TEXT;
    private static Constructor<?> CLICK_EVENT_CONSTRUCTOR;
    private static Constructor<?> HOVER_EVENT_CONSTRUCTOR;
    
    static {
        boolean newApi = false;
        try {
            for (Class<?> innerClass : ClickEvent.class.getDeclaredClasses()) {
                if (innerClass.getSimpleName().equals("OpenUrl")) {
                    newApi = true;
                    break;
                }
            }
            
            if (newApi) {
                System.out.println("[x7t Name Checker] Using new MC 1.21.5+ text event API");
            }
        } catch (Exception e) {
            newApi = false;
        }
        
        if (!newApi) {
            System.out.println("[x7t Name Checker] Using legacy MC 1.21.4 text event API");
            
            try {
                Class<?> clickActionClass = null;
                for (Class<?> innerClass : ClickEvent.class.getDeclaredClasses()) {
                    if (innerClass.isEnum()) {
                        clickActionClass = innerClass;
                        break;
                    }
                }
                
                if (clickActionClass != null) {
                    Object[] enumConstants = clickActionClass.getEnumConstants();
                    for (Object constant : enumConstants) {
                        String name = ((Enum<?>) constant).name();
                        switch (name) {
                            case "OPEN_URL":
                                CLICK_ACTION_OPEN_URL = constant;
                                break;
                            case "RUN_COMMAND":
                                CLICK_ACTION_RUN_COMMAND = constant;
                                break;
                            case "SUGGEST_COMMAND":
                                CLICK_ACTION_SUGGEST_COMMAND = constant;
                                break;
                            case "COPY_TO_CLIPBOARD":
                                CLICK_ACTION_COPY_TO_CLIPBOARD = constant;
                                break;
                        }
                    }
                    
                    for (Constructor<?> c : ClickEvent.class.getDeclaredConstructors()) {
                        Class<?>[] params = c.getParameterTypes();
                        if (params.length == 2 && params[0] == clickActionClass && params[1] == String.class) {
                            CLICK_EVENT_CONSTRUCTOR = c;
                            CLICK_EVENT_CONSTRUCTOR.setAccessible(true);
                            break;
                        }
                    }
                }
                
                Class<?> hoverActionClass = null;
                for (Class<?> innerClass : HoverEvent.class.getDeclaredClasses()) {
                    if (innerClass.getSimpleName().equals("Action") || 
                        (!innerClass.isEnum() && !innerClass.isInterface())) {
                        try {
                            for (java.lang.reflect.Field field : innerClass.getDeclaredFields()) {
                                if (field.getName().equals("SHOW_TEXT")) {
                                    field.setAccessible(true);
                                    HOVER_ACTION_SHOW_TEXT = field.get(null);
                                    hoverActionClass = innerClass;
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    if (HOVER_ACTION_SHOW_TEXT != null) break;
                }
                
                if (HOVER_ACTION_SHOW_TEXT == null) {
                    for (java.lang.reflect.Field field : HoverEvent.class.getDeclaredFields()) {
                        try {
                            field.setAccessible(true);
                            if (field.getName().toUpperCase().contains("SHOW") || 
                                field.getName().toUpperCase().contains("TEXT")) {
                                Object val = field.get(null);
                                if (val != null) {
                                    HOVER_ACTION_SHOW_TEXT = val;
                                    hoverActionClass = val.getClass();
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                
                if (hoverActionClass != null) {
                    for (Constructor<?> c : HoverEvent.class.getDeclaredConstructors()) {
                        Class<?>[] params = c.getParameterTypes();
                        if (params.length == 2) {
                            HOVER_EVENT_CONSTRUCTOR = c;
                            HOVER_EVENT_CONSTRUCTOR.setAccessible(true);
                            break;
                        }
                    }
                }
                
                System.out.println("[x7t Name Checker] Legacy API initialized:");
                System.out.println("[x7t Name Checker]   OPEN_URL: " + CLICK_ACTION_OPEN_URL);
                System.out.println("[x7t Name Checker]   RUN_COMMAND: " + CLICK_ACTION_RUN_COMMAND);
                System.out.println("[x7t Name Checker]   COPY_TO_CLIPBOARD: " + CLICK_ACTION_COPY_TO_CLIPBOARD);
                System.out.println("[x7t Name Checker]   SHOW_TEXT: " + HOVER_ACTION_SHOW_TEXT);
                System.out.println("[x7t Name Checker]   ClickEvent constructor: " + CLICK_EVENT_CONSTRUCTOR);
                System.out.println("[x7t Name Checker]   HoverEvent constructor: " + HOVER_EVENT_CONSTRUCTOR);
            } catch (Exception ex) {
                System.err.println("[x7t Name Checker] Failed to initialize legacy API: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        USE_NEW_API = newApi;
    }
    
    public static HoverEvent showText(Text text) {
        try {
            if (USE_NEW_API) {
                for (Class<?> innerClass : HoverEvent.class.getDeclaredClasses()) {
                    if (innerClass.getSimpleName().equals("ShowText")) {
                        Constructor<?> constructor = innerClass.getConstructor(Text.class);
                        return (HoverEvent) constructor.newInstance(text);
                    }
                }
            } else if (HOVER_EVENT_CONSTRUCTOR != null && HOVER_ACTION_SHOW_TEXT != null) {
                return (HoverEvent) HOVER_EVENT_CONSTRUCTOR.newInstance(HOVER_ACTION_SHOW_TEXT, text);
            }
        } catch (Exception e) {
            System.err.println("[x7t Name Checker] Failed to create HoverEvent: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    public static ClickEvent copyToClipboard(String text) {
        try {
            if (USE_NEW_API) {
                for (Class<?> innerClass : ClickEvent.class.getDeclaredClasses()) {
                    if (innerClass.getSimpleName().equals("CopyToClipboard")) {
                        Constructor<?> constructor = innerClass.getConstructor(String.class);
                        return (ClickEvent) constructor.newInstance(text);
                    }
                }
            } else if (CLICK_EVENT_CONSTRUCTOR != null && CLICK_ACTION_COPY_TO_CLIPBOARD != null) {
                return (ClickEvent) CLICK_EVENT_CONSTRUCTOR.newInstance(CLICK_ACTION_COPY_TO_CLIPBOARD, text);
            }
        } catch (Exception e) {
            System.err.println("[x7t Name Checker] Failed to create CopyToClipboard: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    public static ClickEvent openUrl(String url) {
        try {
            if (USE_NEW_API) {
                for (Class<?> innerClass : ClickEvent.class.getDeclaredClasses()) {
                    if (innerClass.getSimpleName().equals("OpenUrl")) {
                        Constructor<?> constructor = innerClass.getConstructor(URI.class);
                        return (ClickEvent) constructor.newInstance(URI.create(url));
                    }
                }
            } else if (CLICK_EVENT_CONSTRUCTOR != null && CLICK_ACTION_OPEN_URL != null) {
                return (ClickEvent) CLICK_EVENT_CONSTRUCTOR.newInstance(CLICK_ACTION_OPEN_URL, url);
            }
        } catch (Exception e) {
            System.err.println("[x7t Name Checker] Failed to create OpenUrl: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    public static ClickEvent runCommand(String command) {
        try {
            if (USE_NEW_API) {
                for (Class<?> innerClass : ClickEvent.class.getDeclaredClasses()) {
                    if (innerClass.getSimpleName().equals("RunCommand")) {
                        Constructor<?> constructor = innerClass.getConstructor(String.class);
                        return (ClickEvent) constructor.newInstance(command);
                    }
                }
            } else if (CLICK_EVENT_CONSTRUCTOR != null && CLICK_ACTION_RUN_COMMAND != null) {
                return (ClickEvent) CLICK_EVENT_CONSTRUCTOR.newInstance(CLICK_ACTION_RUN_COMMAND, command);
            }
        } catch (Exception e) {
            System.err.println("[x7t Name Checker] Failed to create RunCommand: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    public static ClickEvent suggestCommand(String command) {
        try {
            if (USE_NEW_API) {
                for (Class<?> innerClass : ClickEvent.class.getDeclaredClasses()) {
                    if (innerClass.getSimpleName().equals("SuggestCommand")) {
                        Constructor<?> constructor = innerClass.getConstructor(String.class);
                        return (ClickEvent) constructor.newInstance(command);
                    }
                }
            } else if (CLICK_EVENT_CONSTRUCTOR != null && CLICK_ACTION_SUGGEST_COMMAND != null) {
                return (ClickEvent) CLICK_EVENT_CONSTRUCTOR.newInstance(CLICK_ACTION_SUGGEST_COMMAND, command);
            }
        } catch (Exception e) {
            System.err.println("[x7t Name Checker] Failed to create SuggestCommand: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
