package za.co.entelect.challenge.player.bootstrapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import za.co.entelect.challenge.botrunners.local.BotRunnerFactory;
import za.co.entelect.challenge.botrunners.local.LocalBotRunner;
import za.co.entelect.challenge.config.BotMetadata;
import za.co.entelect.challenge.config.GameRunnerConfig;
import za.co.entelect.challenge.config.TournamentConfig;
import za.co.entelect.challenge.enums.EnvironmentVariable;
import za.co.entelect.challenge.game.contracts.player.Player;
import za.co.entelect.challenge.player.BotPlayer;
import za.co.entelect.challenge.player.ConsolePlayer;
import za.co.entelect.challenge.player.TournamentPlayer;
import za.co.entelect.challenge.player.entity.BasePlayer;
import za.co.entelect.challenge.storage.AzureBlobStorageService;
import za.co.entelect.challenge.utils.ZipUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerBootstrapper {

    private static final Logger LOGGER = LogManager.getLogger(PlayerBootstrapper.class);

    public List<Player> loadPlayers(GameRunnerConfig gameRunnerConfig) throws Exception {
        List<Player> players = new ArrayList<>();

        // During a tournament match we need retrieve the submitted bots from storage.
        // Once we retrieve them we can extract and read in the bot information
        if (gameRunnerConfig.isTournamentMode) {
            TournamentConfig tournamentConfig = gameRunnerConfig.tournamentConfig;

            LOGGER.info("Retrieving bot path from directory");
            String playerAEnv = System.getenv(EnvironmentVariable.PLAYER_A.name());
            String playerBEnv = System.getenv(EnvironmentVariable.PLAYER_B.name());

            LOGGER.info("Downloading bots");
            AzureBlobStorageService storageService = new AzureBlobStorageService(tournamentConfig.connectionString);
            File playerAZip = storageService.getFile(playerAEnv, String.format("./tournament-tmp/player-%s.zip", UUID.randomUUID()), tournamentConfig.botsContainer);
            File playerBZip = storageService.getFile(playerBEnv, String.format("./tournament-tmp/player-%s.zip", UUID.randomUUID()), tournamentConfig.botsContainer);

            gameRunnerConfig.playerAConfig = ZipUtils.extractZip(playerAZip).getPath();
            gameRunnerConfig.playerBConfig = ZipUtils.extractZip(playerBZip).getPath();

            players.add(parsePlayer(gameRunnerConfig.playerAConfig, "A", gameRunnerConfig, gameRunnerConfig.playerAId, playerAZip, 55555));
            players.add(parsePlayer(gameRunnerConfig.playerBConfig, "B", gameRunnerConfig, gameRunnerConfig.playerBId, playerBZip, 55556));
        } else {
            players.add(parsePlayer(gameRunnerConfig.playerAConfig, "A", gameRunnerConfig, gameRunnerConfig.playerAId));
            players.add(parsePlayer(gameRunnerConfig.playerBConfig, "B", gameRunnerConfig, gameRunnerConfig.playerBId));
        }


        return players;
    }

    private Player parsePlayer(String playerConfig, String playerNumber, GameRunnerConfig gameRunnerConfig, String playerId) throws IOException {
        return parsePlayer(playerConfig, playerNumber, gameRunnerConfig, playerId, null, -1);
    }

    private Player parsePlayer(String playerConfig, String playerNumber, GameRunnerConfig gameRunnerConfig, String playerId, File botZip, int apiPort) throws IOException {

        BasePlayer player;

        if (playerConfig.equals("console")) {
            player = new ConsolePlayer(String.format("BotPlayer %s", playerNumber));
        } else {
            BotMetadata botMetaData = BotMetadata.load(playerConfig);
            String playerName = String.format("%s - %s", playerNumber, botMetaData.getNickName());

            if (gameRunnerConfig.isTournamentMode) {
                LOGGER.info("Instantiating tournament player {}", playerName);
                player = new TournamentPlayer(gameRunnerConfig, playerName, apiPort, botZip);
            } else {
                LOGGER.info("Instantiating local player {} with bot {}", playerName, botMetaData.getBotLocation());
                File botFile = new File(botMetaData.getBotDirectory());
                if (!botFile.exists()) {
                    throw new FileNotFoundException(String.format("Could not find %s bot file for %s(%s)", botMetaData.getBotLanguage(), botMetaData.getAuthor(), botMetaData.getNickName()));
                }

                LocalBotRunner botRunner = BotRunnerFactory.createBotRunner(botMetaData, gameRunnerConfig.maximumBotRuntimeMilliSeconds);
                player = new BotPlayer(playerName, botRunner);
            }
        }

        player.setPlayerId(playerId);
        return player;
    }
}
