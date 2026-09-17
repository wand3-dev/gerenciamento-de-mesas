package com.garcom.appmesas;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FirebaseAuthClient {

    private static final String API_KEY = "AIzaSyDkmHXR3uEF1IGCOlpjBq3cij5u43hxLbQ";
    private static final String SIGN_IN_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + API_KEY;
    private static final String SIGN_UP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + API_KEY;
    private static final String UPDATE_PROFILE_URL = "https://identitytoolkit.googleapis.com/v1/accounts:update?key=" + API_KEY;

    private static FirebaseAuthClient instance;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface AuthCallback {
        void onSuccess(String email, String nome, String localId, String idToken);
        void onError(String mensagemErro);
    }

    private FirebaseAuthClient() {}

    public static synchronized FirebaseAuthClient getInstance() {
        if (instance == null) {
            instance = new FirebaseAuthClient();
        }
        return instance;
    }

    /**
     * Realiza login diretamente com o Firebase Auth via REST
     */
    public void fazerLogin(String email, String senha, AuthCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("email", email.trim());
                requestBody.put("senha".equals("password") ? "password" : "password", senha.trim());
                requestBody.put("returnSecureToken", true);

                JSONObject response = postJson(SIGN_IN_URL, requestBody);

                if (response.has("idToken")) {
                    String localId = response.optString("localId", "");
                    String userEmail = response.optString("email", email);
                    String displayName = response.optString("displayName", "");
                    String idToken = response.optString("idToken", "");

                    // Se não tiver displayName, usa o que vem antes do @ no email
                    if (displayName.isEmpty() && userEmail.contains("@")) {
                        displayName = userEmail.substring(0, userEmail.indexOf("@"));
                        displayName = formatarNome(displayName);
                    }

                    final String finalName = displayName;
                    mainHandler.post(() -> callback.onSuccess(userEmail, finalName, localId, idToken));
                } else {
                    String erroMsg = extrairMensagemErro(response);
                    mainHandler.post(() -> callback.onError(erroMsg));
                }
            } catch (Exception e) {
                String msg = traduzirExcecao(e);
                mainHandler.post(() -> callback.onError(msg));
            }
        });
    }

    /**
     * Cria uma nova conta diretamente no Firebase Auth via REST
     */
    public void criarConta(String nome, String email, String senha, AuthCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("email", email.trim());
                requestBody.put("password", senha.trim());
                requestBody.put("returnSecureToken", true);

                JSONObject response = postJson(SIGN_UP_URL, requestBody);

                if (response.has("idToken")) {
                    String localId = response.optString("localId", "");
                    String userEmail = response.optString("email", email);
                    String idToken = response.optString("idToken", "");

                    // Salva o nome de exibição no Firebase caso informado
                    String finalNome = nome.trim();
                    if (!finalNome.isEmpty()) {
                        try {
                            JSONObject profileBody = new JSONObject();
                            profileBody.put("idToken", idToken);
                            profileBody.put("displayName", finalNome);
                            profileBody.put("returnSecureToken", false);
                            postJson(UPDATE_PROFILE_URL, profileBody);
                        } catch (Exception ignored) {}
                    } else if (userEmail.contains("@")) {
                        finalNome = formatarNome(userEmail.substring(0, userEmail.indexOf("@")));
                    }

                    final String nomeResolvido = finalNome;
                    mainHandler.post(() -> callback.onSuccess(userEmail, nomeResolvido, localId, idToken));
                } else {
                    String erroMsg = extrairMensagemErro(response);
                    mainHandler.post(() -> callback.onError(erroMsg));
                }
            } catch (Exception e) {
                String msg = traduzirExcecao(e);
                mainHandler.post(() -> callback.onError(msg));
            }
        });
    }

    private JSONObject postJson(String urlStr, JSONObject body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");

        byte[] postData = body.toString().getBytes("UTF-8");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(postData);
            os.flush();
        }

        int statusCode = conn.getResponseCode();
        InputStream is = (statusCode >= 200 && statusCode < 300) ? conn.getInputStream() : conn.getErrorStream();

        if (is == null) {
            return new JSONObject();
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        String respStr = sb.toString().trim();
        if (respStr.startsWith("{")) {
            return new JSONObject(respStr);
        }
        return new JSONObject();
    }

    private String extrairMensagemErro(JSONObject response) {
        if (response.has("error")) {
            JSONObject errObj = response.optJSONObject("error");
            if (errObj != null) {
                String message = errObj.optString("message", "");
                return traduzirCodigoFirebase(message);
            }
        }
        return "Erro de autenticação no Firebase.";
    }

    private String traduzirCodigoFirebase(String codigo) {
        if (codigo == null) return "Erro desconhecido.";
        if (codigo.contains("EMAIL_NOT_FOUND")) {
            return "Email não cadastrado no Firebase.";
        } else if (codigo.contains("INVALID_PASSWORD") || codigo.contains("INVALID_LOGIN_CREDENTIALS")) {
            return "Senha incorreta!";
        } else if (codigo.contains("USER_DISABLED")) {
            return "Esta conta foi desativada pelo administrador.";
        } else if (codigo.contains("EMAIL_EXISTS")) {
            return "Este email já possui cadastro. Faça o login.";
        } else if (codigo.contains("OPERATION_NOT_ALLOWED")) {
            return "Login por email e senha não habilitado no console do Firebase.";
        } else if (codigo.contains("TOO_MANY_ATTEMPTS_TRY_LATER")) {
            return "Muitas tentativas sem sucesso. Tente novamente mais tarde.";
        } else if (codigo.contains("WEAK_PASSWORD")) {
            return "A senha deve conter no mínimo 6 caracteres.";
        } else if (codigo.contains("INVALID_EMAIL")) {
            return "Formato de e-mail inválido.";
        }
        return "Erro: " + codigo;
    }

    private String traduzirExcecao(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "Falha de conexão com o Firebase.";
        if (msg.contains("Unable to resolve host") || msg.contains("Failed to connect")) {
            return "Sem conexão com a internet para autenticar no Firebase.";
        } else if (msg.contains("timeout")) {
            return "Tempo de conexão esgotado. Verifique sua rede.";
        }
        return "Erro: " + msg;
    }

    private String formatarNome(String str) {
        if (str == null || str.isEmpty()) return "Garçom";
        String[] partes = str.replace(".", " ").replace("_", " ").replace("-", " ").split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            if (p.length() > 0) {
                sb.append(Character.toUpperCase(p.charAt(0)));
                if (p.length() > 1) {
                    sb.append(p.substring(1).toLowerCase());
                }
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}
