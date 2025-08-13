package com.morreu.nyris.manager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.morreu.nyris.data.MineData;

public interface DataManager {
    
    /**
     * Salva alguma mina no sistema de persistencia
     * @param mine dados da mina a ser salvo
     * @return CompletableFuture dizendo sucesso ou falha
     */
    CompletableFuture<Boolean> saveMine(MineData mine);
    
    /**
     * carrega todas as minas do sistema de persistencia
     * @return CompletableFuture com lista de mians carregadas
     */
    CompletableFuture<List<MineData>> loadMines();
    
    /**
     * Remove uma mina do sistema de opersistencia
     * @param mine dados da mina a ser removida
     * @return CompletableFuture indicando sucesso ou falha
     */
    CompletableFuture<Boolean> removeMine(MineData mine);
    
    /**
     * Atualiza uma mina existente no sistema de persistência
     * @param mine Dados atualizados da mina
     * @return CompletableFuture indicando sucesso ou falha
     */
    CompletableFuture<Boolean> updateMine(MineData mine);
    
    /**
     * Verifica se o sistema de persistência está funcionando
     * @return CompletableFuture indicando se está online
     */
    CompletableFuture<Boolean> isConnected();

    /**
     * Fecha conexões e libera recursos
     */
    void close();
}
