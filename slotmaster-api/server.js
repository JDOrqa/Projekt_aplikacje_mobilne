const express = require('express');
const sqlite3 = require('sqlite3').verbose();
const cors = require('cors');
const app = express();
const port = 3000;

// Middleware
app.use(cors());
app.use(express.json());

// SQLite Database
const db = new sqlite3.Database('./slotmaster.db', (err) => {
    if (err) {
        console.error('❌ Błąd SQLite:', err.message);
    } else {
        console.log('✅ Połączono z SQLite database');
        initializeDatabase();
    }
});

// Inicjalizacja bazy danych
function initializeDatabase() {
    db.run(`CREATE TABLE IF NOT EXISTS game_history (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id TEXT NOT NULL,
        game_date TEXT NOT NULL,
        final_balance INTEGER NOT NULL,
        spins_count INTEGER NOT NULL,
        biggest_win INTEGER NOT NULL,
        created_at TEXT NOT NULL
    )`, (err) => {
        if (err) {
            console.error('❌ Błąd tworzenia tabeli:', err);
        } else {
            console.log('✅ Tabela game_history gotowa');
        }
    });

    db.run(`CREATE TABLE IF NOT EXISTS game_state (
        user_id TEXT PRIMARY KEY,
        balance INTEGER NOT NULL DEFAULT 5000,
        spins_count INTEGER NOT NULL DEFAULT 0,
        biggest_win INTEGER NOT NULL DEFAULT 0,
        visited_locations TEXT,
        selected_lines INTEGER NOT NULL DEFAULT 1,
        last_shake_time INTEGER NOT NULL DEFAULT 0,
        updated_at TEXT NOT NULL
    )`, (err) => {
        if (err) {
            console.error('❌ Błąd tworzenia tabeli game_state:', err);
        } else {
            console.log('✅ Tabela game_state gotowa');
        }
    });
}

// Funkcja do wykonywania zapytań
function executeQuery(sql, params = [], callback) {
    console.log('🗃️  Wykonuję zapytanie:', sql, 'Params:', params);
    
    if (sql.trim().toUpperCase().startsWith('SELECT')) {
        db.all(sql, params, (err, rows) => {
            if (err) {
                console.error('❌ Błąd SELECT:', err);
                callback(err, null);
            } else {
                callback(null, rows);
            }
        });
    } else {
        db.run(sql, params, function(err) {
            if (err) {
                console.error('❌ Błąd INSERT/UPDATE/DELETE:', err);
                callback(err, null);
            } else {
                callback(null, { changes: this.changes, lastID: this.lastID });
            }
        });
    }
}

// 🔽 ENDPOINTY BEZ /api/ (dla localhost)
app.get('/', (req, res) => {
    res.json({ 
        message: 'SlotMaster API Root',
        endpoints: {
            status: '/status',
            admin: '/admin', 
            apiStatus: '/api/status',
            apiAdmin: '/api/admin'
        }
    });
});

app.get('/status', (req, res) => {
    res.json({ 
        status: 'OK', 
        message: 'SlotMaster API (no prefix) działa!',
        timestamp: new Date().toISOString()
    });
});

app.get('/admin', (req, res) => {
    const sql = 'SELECT * FROM game_history ORDER BY created_at DESC LIMIT 20';
    
    executeQuery(sql, [], (err, result) => {
        if (err) {
            return res.status(500).send('Błąd bazy danych');
        }
        
        let html = `
        <!DOCTYPE html>
        <html>
        <head>
            <title>SlotMaster - Panel Admina (No Prefix)</title>
            <style>
                body { font-family: Arial; margin: 20px; }
                table { border-collapse: collapse; width: 100%; }
                th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                th { background-color: #f2f2f2; }
                tr:nth-child(even) { background-color: #f9f9f9; }
            </style>
        </head>
        <body>
            <h1>🎰 SlotMaster - Panel Admina (No Prefix)</h1>
            <p>Liczba rekordów: <strong>${result ? result.length : 0}</strong></p>
            <table>
                <tr>
                    <th>ID</th>
                    <th>User ID</th>
                    <th>Data gry</th>
                    <th>Saldo</th>
                    <th>Spiny</th>
                    <th>Wygrana</th>
                    <th>Utworzono</th>
                </tr>
        `;
        
        if (result && result.length > 0) {
            result.forEach(record => {
                html += `
                <tr>
                    <td>${record.id}</td>
                    <td>${record.user_id}</td>
                    <td>${record.game_date}</td>
                    <td>${record.final_balance}</td>
                    <td>${record.spins_count}</td>
                    <td>${record.biggest_win}</td>
                    <td>${record.created_at}</td>
                </tr>
                `;
            });
        } else {
            html += `<tr><td colspan="7">Brak danych</td></tr>`;
        }
        
        html += `
            </table>
            <br>
            <a href="/api/admin">API Admin Panel</a> | 
            <a href="/api/status">API Status</a>
        </body>
        </html>
        `;
        
        res.send(html);
    });
});

// 🔽 ROZSZERZONY PANEL ADMINA Z /api/ PREFIX
app.get('/api/status', (req, res) => {
    res.json({ 
        status: 'OK', 
        message: 'SlotMaster API z /api/ prefix działa!',
        database: 'SQLite',
        timestamp: new Date().toISOString()
    });
});

app.get('/api/admin', (req, res) => {
    const sql = `
        SELECT gh.*, gs.balance as current_balance 
        FROM game_history gh 
        LEFT JOIN game_state gs ON gh.user_id = gs.user_id 
        ORDER BY gh.created_at DESC 
        LIMIT 100
    `;
    
    executeQuery(sql, [], (err, result) => {
        if (err) {
            return res.status(500).send('Błąd bazy danych');
        }
        
        // Pobierz statystyki
        const statsSql = `
            SELECT 
                COUNT(*) as total_records,
                COUNT(DISTINCT user_id) as total_users,
                SUM(spins_count) as total_spins,
                MAX(biggest_win) as max_win
            FROM game_history
        `;
        
        executeQuery(statsSql, [], (err, stats) => {
            if (err) {
                return res.status(500).send('Błąd pobierania statystyk');
            }
            
            const statistics = stats && stats.length > 0 ? stats[0] : {};
            
            let html = `
            <!DOCTYPE html>
            <html>
            <head>
                <title>SlotMaster - Panel Admina</title>
                <style>
                    body { font-family: Arial; margin: 20px; }
                    table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                    th { background-color: #f2f2f2; }
                    tr:nth-child(even) { background-color: #f9f9f9; }
                    .danger { background-color: #ffebee; }
                    .btn { 
                        padding: 5px 10px; 
                        margin: 2px; 
                        border: none; 
                        border-radius: 3px; 
                        cursor: pointer; 
                        text-decoration: none;
                        display: inline-block;
                        font-size: 12px;
                    }
                    .btn-danger { background-color: #f44336; color: white; }
                    .btn-warning { background-color: #ff9800; color: white; }
                    .btn-success { background-color: #4caf50; color: white; }
                    .btn-info { background-color: #2196f3; color: white; }
                    .stats { 
                        background-color: #e3f2fd; 
                        padding: 15px; 
                        border-radius: 5px; 
                        margin-bottom: 20px;
                    }
                    .actions { margin-bottom: 20px; }
                    .section { margin-bottom: 30px; }
                </style>
            </head>
            <body>
                <h1>🎰 SlotMaster - Panel Admina</h1>
                
                <div class="stats">
                    <h3>📊 Statystyki</h3>
                    <p><strong>Rekordy historii:</strong> ${statistics.total_records || 0}</p>
                    <p><strong>Unikalni użytkownicy:</strong> ${statistics.total_users || 0}</p>
                    <p><strong>Łączna liczba spinów:</strong> ${statistics.total_spins || 0}</p>
                    <p><strong>Maksymalna wygrana:</strong> ${statistics.max_win || 0}</p>
                </div>

                <div class="actions">
                    <h3>⚡ Szybkie akcje</h3>
                    <a href="/api/admin/clear-all" class="btn btn-danger" onclick="return confirm('Czy na pewno chcesz usunąć WSZYSTKIE dane?')">🗑️ Usuń wszystkie dane</a>
                    <a href="/api/admin/clear-old" class="btn btn-warning" onclick="return confirm('Usunąć rekordy starsze niż 7 dni?')">🧹 Wyczyść stare rekordy</a>
                    <a href="/api/admin" class="btn btn-info">🔄 Odśwież</a>
                </div>

                <div class="section">
                    <h3>📋 Ostatnie rekordy gry (max 100)</h3>
                    <p>Liczba rekordów: <strong>${result ? result.length : 0}</strong></p>
                    <table>
                        <tr>
                            <th>ID</th>
                            <th>User ID</th>
                            <th>Data gry</th>
                            <th>Saldo</th>
                            <th>Spiny</th>
                            <th>Wygrana</th>
                            <th>Utworzono</th>
                            <th>Akcje</th>
                        </tr>
            `;
            
            if (result && result.length > 0) {
                result.forEach(record => {
                    html += `
                    <tr>
                        <td>${record.id}</td>
                        <td title="${record.user_id}">${record.user_id.substring(0, 20)}...</td>
                        <td>${record.game_date}</td>
                        <td>${record.final_balance}</td>
                        <td>${record.spins_count}</td>
                        <td>${record.biggest_win}</td>
                        <td>${record.created_at}</td>
                        <td>
                            <a href="/api/admin/delete-record/${record.id}" class="btn btn-danger" onclick="return confirm('Usunąć ten rekord?')">🗑️</a>
                            <a href="/api/admin/delete-user/${encodeURIComponent(record.user_id)}" class="btn btn-warning" onclick="return confirm('Usunąć WSZYSTKIE dane usera ${record.user_id}?')">👤</a>
                        </td>
                    </tr>
                    `;
                });
            } else {
                html += `<tr><td colspan="8">Brak danych</td></tr>`;
            }
            
            html += `
                    </table>
                </div>

                <div class="section">
                    <h3>👥 Aktywni użytkownicy</h3>
            `;
            
            // Pobierz aktywnych użytkowników
            const usersSql = `
                SELECT 
                    user_id,
                    COUNT(*) as games_count,
                    SUM(spins_count) as total_spins,
                    MAX(created_at) as last_activity
                FROM game_history 
                GROUP BY user_id 
                ORDER BY last_activity DESC 
                LIMIT 20
            `;
            
            executeQuery(usersSql, [], (err, users) => {
                if (err) {
                    html += `<p>Błąd pobierania użytkowników</p>`;
                } else {
                    html += `<table>
                        <tr>
                            <th>User ID</th>
                            <th>Liczba gier</th>
                            <th>Spiny</th>
                            <th>Ostatnia aktywność</th>
                            <th>Akcje</th>
                        </tr>`;
                    
                    if (users && users.length > 0) {
                        users.forEach(user => {
                            html += `
                            <tr>
                                <td title="${user.user_id}">${user.user_id.substring(0, 25)}...</td>
                                <td>${user.games_count}</td>
                                <td>${user.total_spins}</td>
                                <td>${user.last_activity}</td>
                                <td>
                                    <a href="/api/admin/delete-user/${encodeURIComponent(user.user_id)}" class="btn btn-danger" onclick="return confirm('Usunąć WSZYSTKIE dane usera ${user.user_id}?')">🗑️ Usuń</a>
                                </td>
                            </tr>
                            `;
                        });
                    } else {
                        html += `<tr><td colspan="5">Brak użytkowników</td></tr>`;
                    }
                    
                    html += `</table>`;
                }
                
                html += `
                </div>
                
                <div style="margin-top: 30px; padding: 15px; background-color: #f5f5f5; border-radius: 5px;">
                    <h4>ℹ️ Informacje</h4>
                    <p><strong>API Status:</strong> <a href="/api/status">/api/status</a></p>
                    <p><strong>Baza danych:</strong> slotmaster.db</p>
                    <p><strong>Endpointy API:</strong></p>
                    <ul>
                        <li><code>GET /api/status</code> - Status API</li>
                        <li><code>GET /api/admin</code> - Panel admina (ten)</li>
                        <li><code>GET /api/admin/delete-record/:id</code> - Usuń rekord</li>
                        <li><code>GET /api/admin/delete-user/:userId</code> - Usuń usera</li>
                        <li><code>GET /api/admin/clear-all</code> - Wyczyść wszystko</li>
                        <li><code>GET /api/admin/clear-old</code> - Wyczyść stare</li>
                    </ul>
                </div>
                
                <script>
                    function confirmAction(message) {
                        return confirm(message || 'Czy na pewno chcesz wykonać tę akcję?');
                    }
                </script>
            </body>
            </html>
                `;
                
                res.send(html);
            });
        });
    });
});

// ENDPOINT: Usuń pojedynczy rekord
app.get('/api/admin/delete-record/:id', (req, res) => {
    const recordId = req.params.id;
    
    console.log('🗑️  Usuwanie rekordu:', recordId);
    
    const sql = 'DELETE FROM game_history WHERE id = ?';
    
    executeQuery(sql, [recordId], (err, result) => {
        if (err) {
            console.error('❌ Błąd usuwania rekordu:', err);
            return res.redirect('/api/admin?error=Błąd usuwania rekordu');
        }
        
        console.log('✅ Usunięto rekordów:', result.changes);
        res.redirect('/api/admin?success=Rekord usunięty pomyślnie');
    });
});

// ENDPOINT: Usuń wszystkie dane usera
app.get('/api/admin/delete-user/:userId', (req, res) => {
    const userId = decodeURIComponent(req.params.userId);
    
    console.log('🗑️  Usuwanie danych usera:', userId);
    
    const deleteHistorySql = 'DELETE FROM game_history WHERE user_id = ?';
    const deleteStateSql = 'DELETE FROM game_state WHERE user_id = ?';
    
    executeQuery(deleteHistorySql, [userId], (err, historyResult) => {
        if (err) {
            console.error('❌ Błąd usuwania historii usera:', err);
            return res.redirect('/api/admin?error=Błąd usuwania danych usera');
        }
        
        executeQuery(deleteStateSql, [userId], (err, stateResult) => {
            if (err) {
                console.error('❌ Błąd usuwania stanu usera:', err);
                // Kontynuuj nawet jeśli błąd - może nie mieć stanu
            }
            
            console.log('✅ Usunięto dane usera:', {
                historyRecords: historyResult.changes,
                stateRecords: stateResult ? stateResult.changes : 0
            });
            
            res.redirect('/api/admin?success=Dane usera usunięte pomyślnie');
        });
    });
});

// ENDPOINT: Wyczyść wszystkie dane
app.get('/api/admin/clear-all', (req, res) => {
    console.log('🗑️  Czyszczenie WSZYSTKICH danych');
    
    const clearHistorySql = 'DELETE FROM game_history';
    const clearStateSql = 'DELETE FROM game_state';
    
    executeQuery(clearHistorySql, [], (err, historyResult) => {
        if (err) {
            console.error('❌ Błąd czyszczenia historii:', err);
            return res.redirect('/api/admin?error=Błąd czyszczenia danych');
        }
        
        executeQuery(clearStateSql, [], (err, stateResult) => {
            if (err) {
                console.error('❌ Błąd czyszczenia stanu:', err);
                // Kontynuuj
            }
            
            console.log('✅ Wyczyszczono wszystkie dane:', {
                historyRecords: historyResult.changes,
                stateRecords: stateResult ? stateResult.changes : 0
            });
            
            res.redirect('/api/admin?success=Wszystkie dane zostały wyczyszczone');
        });
    });
});

// ENDPOINT: Wyczyść stare rekordy (starsze niż 7 dni)
app.get('/api/admin/clear-old', (req, res) => {
    console.log('🧹 Czyszczenie starych rekordów (7+ dni)');
    
    const sql = 'DELETE FROM game_history WHERE date(created_at) < date("now", "-7 days")';
    
    executeQuery(sql, [], (err, result) => {
        if (err) {
            console.error('❌ Błąd czyszczenia starych rekordów:', err);
            return res.redirect('/api/admin?error=Błąd czyszczenia starych rekordów');
        }
        
        console.log('✅ Usunięto starych rekordów:', result.changes);
        res.redirect('/api/admin?success=Usunięto stare rekordy (starsze niż 7 dni)');
    });
});

// 🔽 POZOSTAŁE ENDPOINTY Z /api/ PREFIX
app.get('/api/shared-user-id', (req, res) => {
    console.log('🔗 Żądanie wspólnego userId');
    
    const sql = 'SELECT user_id FROM game_history ORDER BY created_at DESC LIMIT 1';
    
    executeQuery(sql, [], (err, result) => {
        if (err) {
            console.error('❌ Błąd bazy danych:', err);
            return res.status(500).json({ error: 'Błąd bazy danych' });
        }
        
        if (result && result.length > 0) {
            const existingUserId = result[0].user_id;
            console.log('✅ Używam istniejącego userId:', existingUserId);
            res.json({ success: true, userId: existingUserId });
        } else {
            const newUserId = `user_${Date.now()}`;
            console.log('🆕 Tworzę nowe userId:', newUserId);
            res.json({ success: true, userId: newUserId });
        }
    });
});

app.get('/api/users', (req, res) => {
    console.log('👥 Pobieranie listy userów');
    
    const sql = `
        SELECT DISTINCT user_id, MAX(created_at) as last_activity, 
               (SELECT balance FROM game_state WHERE user_id = game_history.user_id) as balance
        FROM game_history 
        GROUP BY user_id 
        ORDER BY last_activity DESC
        LIMIT 10
    `;
    
    executeQuery(sql, [], (err, result) => {
        if (err) {
            console.error('❌ Błąd pobierania userów:', err);
            return res.status(500).json({ error: 'Błąd bazy danych' });
        }
        
        console.log(`✅ Znaleziono ${result ? result.length : 0} userów`);
        res.json(result || []);
    });
});

app.post('/api/users', (req, res) => {
    const { userName } = req.body;
    
    console.log('🆕 Tworzenie nowego usera:', userName);
    
    if (!userName || userName.trim().length === 0) {
        return res.status(400).json({ error: 'Nazwa usera jest wymagana' });
    }
    
    const newUserId = `user_${userName.toLowerCase().replace(/[^a-z0-9]/g, '_')}_${Date.now()}`;
    
    console.log('✅ Utworzono nowego usera:', newUserId);
    res.json({ success: true, userId: newUserId, userName: userName });
});

app.post('/api/game-state', (req, res) => {
    const { userId, balance, spinsCount, biggestWin, visitedLocations, selectedLines, lastShakeTime } = req.body;
    
    console.log('💾 Zapisywanie stanu gry:', { 
        userId, balance, spinsCount, biggestWin, 
        selectedLines, lastShakeTime 
    });
    
    const sql = `
        INSERT OR REPLACE INTO game_state 
        (user_id, balance, spins_count, biggest_win, visited_locations, selected_lines, last_shake_time, updated_at) 
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `;
    
    const visitedLocationsStr = JSON.stringify(visitedLocations || []);
    const updatedAt = new Date().toISOString();
    
    executeQuery(sql, [
        userId, balance, spinsCount, biggestWin, 
        visitedLocationsStr, selectedLines, lastShakeTime, updatedAt
    ], (err) => {
        if (err) {
            console.error('❌ Błąd zapisu stanu gry:', err);
            return res.status(500).json({ error: 'Błąd zapisu stanu' });
        }
        console.log('✅ Stan gry zapisany');
        res.json({ success: true });
    });
});

// 🔽 POPRAWIONY ENDPOINT - NIE ZWRACA DOMYŚLNYCH WARTOŚCI!
app.get('/api/game-state/:userId', (req, res) => {
    const userId = req.params.userId;
    
    console.log('📊 Pobieranie stanu gry dla:', userId);
    
    const sql = 'SELECT * FROM game_state WHERE user_id = ?';
    
    executeQuery(sql, [userId], (err, result) => {
        if (err) {
            console.error('❌ Błąd pobierania stanu gry:', err);
            return res.status(500).json({ error: 'Błąd pobierania stanu' });
        }
        
        if (result && result.length > 0) {
            const gameState = result[0];
            console.log('✅ Znaleziono stan gry:', {
                balance: gameState.balance,
                spinsCount: gameState.spins_count,
                biggestWin: gameState.biggest_win,
                selectedLines: gameState.selected_lines,
                lastShakeTime: gameState.last_shake_time
            });
            
            res.json({
                balance: gameState.balance,
                spinsCount: gameState.spins_count,
                biggestWin: gameState.biggest_win,
                visitedLocations: JSON.parse(gameState.visited_locations || '[]'),
                selectedLines: gameState.selected_lines,
                lastShakeTime: gameState.last_shake_time
            });
        } else {
            console.log('📭 Brak stanu gry dla użytkownika - zwracam 404');
            // 🔽 ZMIANA: NIE zwracaj domyślnych wartości!
            res.status(404).json({ error: 'Game state not found' });
        }
    });
});

app.get('/api/game-history/:userId/today', (req, res) => {
    const userId = req.params.userId;
    const today = new Date().toISOString().split('T')[0];
    
    console.log('📅 Sprawdzam dzisiejszy wpis dla:', userId, 'Data:', today);
    
    const sql = `
        SELECT 
            id AS "id",
            user_id AS "userId", 
            game_date AS "gameDate", 
            final_balance AS "finalBalance", 
            spins_count AS "spinsCount", 
            biggest_win AS "biggestWin", 
            created_at AS "createdAt"
        FROM game_history 
        WHERE user_id = ? AND game_date = ?
        ORDER BY created_at DESC 
        LIMIT 1
    `;
    
    executeQuery(sql, [userId, today], (err, result) => {
        if (err) {
            console.error('❌ Błąd sprawdzania:', err);
            return res.status(500).json({ error: 'Błąd bazy danych' });
        }
        const exists = result && result.length > 0;
        console.log('📅 Dzisiejszy wpis istnieje:', exists);
        res.json(result || []);
    });
});

app.get('/api/game-history/:userId', (req, res) => {
    const userId = req.params.userId;
    
    console.log('📊 Pobieram historię dla:', userId);
    
    const sql = `
        SELECT 
            id AS "id",
            user_id AS "userId", 
            game_date AS "gameDate", 
            final_balance AS "finalBalance", 
            spins_count AS "spinsCount", 
            biggest_win AS "biggestWin", 
            created_at AS "createdAt"
        FROM game_history 
        WHERE user_id = ? 
        ORDER BY game_date DESC 
        LIMIT 7
    `;
    
    executeQuery(sql, [userId], (err, result) => {
        if (err) {
            console.error('❌ Błąd pobierania historii:', err);
            return res.status(500).json({ error: 'Błąd bazy danych' });
        }
        
        console.log('✅ Znaleziono wpisów:', result ? result.length : 0);
        res.json(result || []);
    });
});

app.post('/api/game-history', (req, res) => {
    console.log('📨 OTRZYMANO ZAPYTANIE POST:', req.body);
    
    const { userId, gameDate, finalBalance, spinsCount, biggestWin, createdAt } = req.body;
    
    if (!userId || !gameDate) {
        console.error('❌ Brak wymaganych pól:', { userId, gameDate });
        return res.status(400).json({ error: 'Brak userId lub gameDate' });
    }
    
    console.log('💾 Zapisuję dane:', { userId, gameDate, finalBalance, spinsCount, biggestWin, createdAt });
    
    const checkSql = 'SELECT id FROM game_history WHERE user_id = ? AND game_date = ?';
    
    executeQuery(checkSql, [userId, gameDate], (err, result) => {
        if (err) {
            console.error('❌ Błąd sprawdzania:', err);
            return res.status(500).json({ error: 'Błąd bazy danych' });
        }
        
        console.log('🔍 Wynik sprawdzenia:', result);
        
        if (result && result.length > 0) {
            const updateSql = `UPDATE game_history 
                SET final_balance = ?, spins_count = ?, biggest_win = ?, created_at = ?
                WHERE user_id = ? AND game_date = ?`;
            
            console.log('🔄 Aktualizuję istniejący wpis, ID:', result[0].id);
            
            executeQuery(updateSql, [finalBalance, spinsCount, biggestWin, createdAt, userId, gameDate], (err) => {
                if (err) {
                    console.error('❌ Błąd aktualizacji:', err);
                    return res.status(500).json({ error: 'Błąd aktualizacji' });
                }
                console.log('✅ Zaktualizowano wpis');
                res.json({ success: true, action: 'updated' });
            });
        } else {
            const insertSql = `INSERT INTO game_history 
                (user_id, game_date, final_balance, spins_count, biggest_win, created_at) 
                VALUES (?, ?, ?, ?, ?, ?)`;
            
            console.log('🆕 Dodaję nowy wpis');
            
            executeQuery(insertSql, [userId, gameDate, finalBalance, spinsCount, biggestWin, createdAt], (err, result) => {
                if (err) {
                    console.error('❌ Błąd wstawiania:', err);
                    return res.status(500).json({ error: 'Błąd zapisu' });
                }
                console.log('✅ Dodano nowy wpis, ID:', result.lastID);
                res.json({ success: true, action: 'created', id: result.lastID });
            });
        }
    });
});



const crypto = require('crypto');

function hashPassword(password) {
    return crypto.createHash('md5').update(password).digest('hex'); // md5 jest szybkie
}

// 🔽 DODAJ TĄ TABELĘ w initializeDatabase()
db.run(`CREATE TABLE IF NOT EXISTS users (
    username TEXT PRIMARY KEY,
    password TEXT NOT NULL,
    created TEXT DEFAULT CURRENT_TIMESTAMP
)`, (err) => {
    if (err) console.error('Błąd tabeli users:', err);
    else console.log('✅ Tabela users gotowa');
});

// 🔽 REJESTRACJA
app.post('/api/register', (req, res) => {
    const { username, password } = req.body;
    
    if (!username || !password) {
        return res.json({ error: 'Podaj login i hasło' });
    }
    
    const hashed = hashPassword(password);
    
    // Rozpocznij transakcję
    db.serialize(() => {
        db.run('BEGIN TRANSACTION');
        
        // 1. Dodaj do tabeli users
        const sql1 = 'INSERT INTO users (username, password) VALUES (?, ?)';
        db.run(sql1, [username, hashed], function(err) {
            if (err) {
                db.run('ROLLBACK');
                if (err.message.includes('UNIQUE')) {
                    return res.json({ error: 'Login zajęty' });
                }
                return res.json({ error: 'Błąd serwera' });
            }
            
            // 2. Automatycznie utwórz wpis w game_state z tym samym user_id
            const userId = `user_${username}`;
            const sql2 = `INSERT OR IGNORE INTO game_state 
                         (user_id, balance, spins_count, biggest_win, updated_at) 
                         VALUES (?, 5000, 0, 0, ?)`;
            
            db.run(sql2, [userId, new Date().toISOString()], function(err2) {
                if (err2) {
                    db.run('ROLLBACK');
                    console.error('Błąd tworzenia game_state:', err2);
                    return res.json({ error: 'Błąd inicjalizacji gry' });
                }
                
                db.run('COMMIT');
                console.log('✅ Utworzono użytkownika i stan gry:', username);
                res.json({ ok: true, userId: userId });
            });
        });
    });
});

// 🔽 ROZSZERZ LOGOWANIE
app.post('/api/login', (req, res) => {
    const { username, password } = req.body;
    
    if (!username || !password) {
        return res.json({ error: 'Podaj login i hasło' });
    }
    
    const sql = 'SELECT username FROM users WHERE username = ? AND password = ?';
    const hashed = hashPassword(password);
    
    executeQuery(sql, [username, hashed], (err, result) => {
        if (err) {
            return res.json({ error: 'Błąd serwera' });
        }
        
        if (result && result.length > 0) {
            const userId = `user_${username}`;
            
            // 🔽 SPRAWDŹ CZY ISTNIEJE game_state, JAK NIE - UTWÓRZ
            const checkSql = 'SELECT user_id FROM game_state WHERE user_id = ?';
            executeQuery(checkSql, [userId], (err2, result2) => {
                if (err2) {
                    return res.json({ error: 'Błąd sprawdzania stanu gry' });
                }
                
                if (!result2 || result2.length === 0) {
                    // Utwórz stan gry jeśli nie istnieje
                    const insertSql = `INSERT INTO game_state 
                                     (user_id, balance, spins_count, biggest_win, updated_at) 
                                     VALUES (?, 5000, 0, 0, ?)`;
                    executeQuery(insertSql, [userId, new Date().toISOString()], (err3) => {
                        if (err3) {
                            console.error('Błąd tworzenia game_state:', err3);
                        }
                        res.json({ ok: true, username: username, userId: userId });
                    });
                } else {
                    res.json({ ok: true, username: username, userId: userId });
                }
            });
        } else {
            res.json({ error: 'Zły login lub hasło' });
        }
    });
});

// 🔽 SPRAWDŹ CZY LOGIN JEST WOLNY
app.get('/api/check-login/:username', (req, res) => {
    const sql = 'SELECT username FROM users WHERE username = ?';
    executeQuery(sql, [req.params.username], (err, result) => {
        if (err) return res.json({ available: false });
        res.json({ available: !(result && result.length > 0) });
    });
});






app.delete('/api/game-history/:userId', (req, res) => {
    const userId = req.params.userId;
    
    console.log('🗑️  Usuwam historię dla:', userId);
    
    const sql = 'DELETE FROM game_history WHERE user_id = ?';
    
    executeQuery(sql, [userId], (err, result) => {
        if (err) {
            console.error('❌ Błąd usuwania:', err);
            return res.status(500).json({ error: 'Błąd usuwania' });
        }
        console.log('✅ Usunięto wpisów:', result.changes);
        res.json({ success: true, deletedCount: result.changes });
    });
});

app.get('/api/debug/database', (req, res) => {
    const sql = 'SELECT * FROM game_history ORDER BY created_at DESC LIMIT 10';
    
    executeQuery(sql, [], (err, result) => {
        if (err) {
            return res.status(500).json({ error: err.message });
        }
        res.json(result || []);
    });
});







// Uruchom serwer
app.listen(port, '0.0.0.0', () => {
    console.log(`🎰 SlotMaster API działa na http://localhost:${port}`);
    console.log(`📍 Status (no prefix): http://localhost:${port}/status`);
    console.log(`📍 Status (api prefix): http://localhost:${port}/api/status`);
    console.log(`👨‍💼 Admin (no prefix): http://localhost:${port}/admin`);
    console.log(`👨‍💼 Admin (api prefix): http://localhost:${port}/api/admin`);
    console.log(`🌐 Localtunnel: https://projekt-mobilne.loca.lt/api/status`);
    console.log(`🌐 Localtunnel Admin: https://projekt-mobilne.loca.lt/api/admin`);
});