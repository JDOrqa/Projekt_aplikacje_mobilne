const { exec } = require('child_process');

function startTunnel() {
    console.log('🔄 Uruchamianie localtunnel...');
    
    const tunnel = exec('lt --port 3000 --subdomain projekt-mobilne-kraj');
    
    tunnel.stdout.on('data', (data) => {
        console.log(`📡 ${data}`);
    });
    
    tunnel.stderr.on('data', (data) => {
        console.error(`❌ ${data}`);
    });
    
    tunnel.on('close', (code) => {
        console.log(`💥 Localtunnel zamknięty (kod: ${code}), restart za 5s...`);
        setTimeout(startTunnel, 5000);
    });
}

startTunnel();