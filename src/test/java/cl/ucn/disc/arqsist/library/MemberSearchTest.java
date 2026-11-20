/*
 * Copyright (c) 2026. Arquitectura de Sistemas, DISC, UCN, Antofagasta.
 */

package cl.ucn.disc.arqsist.library;

import cl.ucn.disc.arqsist.library.dao.MemberDao;
import cl.ucn.disc.arqsist.library.db.Database;
import cl.ucn.disc.arqsist.library.grpc.MemberClient;
import cl.ucn.disc.arqsist.library.grpc.MemberGrpcService;
import cl.ucn.disc.arqsist.library.model.Member;
import cl.ucn.disc.arqsist.library.service.MemberService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the SearchMembers RPC through the gRPC round trip.
 *
 * Every test starts an in-process server on a fresh in-memory database with
 * three members, created in this order: Ada Lovelace, Grace Hopper, Alan Turing.
 */
final class MemberSearchTest {

    /**
     * The Constructor. JUnit creates one instance for each test method.
     */
    MemberSearchTest() {
    }

    /**
     * The in-process gRPC server.
     */
    private Server server;

    /**
     * The client under test.
     */
    private MemberClient client;

    /**
     * Create the database, the three members, the server, and the client.
     *
     * @throws Exception if the database or the server fails.
     */
    @BeforeEach
    void setUp() throws Exception {
        // The in-memory database keeps each test isolated.
        Database db = new Database("jdbc:sqlite::memory:");
        MemberService memberService = new MemberService(new MemberDao(db.connectionSource()));
        memberService.register(new Member("Ada Lovelace", "ada@example.com"));
        memberService.register(new Member("Grace Hopper", "grace@example.com"));
        memberService.register(new Member("Alan Turing", "alan@example.com"));

        // The in-process transport removes the network from the test.
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(new MemberGrpcService(memberService))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        client = new MemberClient(channel);
    }

    /**
     * Close the client and stop the server.
     */
    @AfterEach
    void tearDown() {
        client.close();
        server.shutdownNow();
    }

    /**
     * Case 1: "ada" finds Ada Lovelace only.
     */
    @Test
    void lowerCaseQueryFindsOneMember() {
        assertEquals(List.of("Ada Lovelace"), names(client.search("ada")));
    }

    /**
     * Case 2: "ADA" finds Ada Lovelace. The match is case-insensitive.
     */
    @Test
    void upperCaseQueryFindsOneMember() {
        assertEquals(List.of("Ada Lovelace"), names(client.search("ADA")));
    }

    /**
     * Case 3: " ada " finds Ada Lovelace. The service trims the query.
     */
    @Test
    void paddedQueryIsTrimmed() {
        assertEquals(List.of("Ada Lovelace"), names(client.search(" ada ")));
    }

    /**
     * Case 4: "a" finds the three members, ordered by ID.
     */
    @Test
    void commonLetterFindsAllMembersOrderedById() {
        List<Member> members = client.search("a");
        assertEquals(List.of("Ada Lovelace", "Grace Hopper", "Alan Turing"), names(members));
        assertTrue(members.get(0).getId() < members.get(1).getId());
        assertTrue(members.get(1).getId() < members.get(2).getId());
    }

    /**
     * Case 5: "zzz" gives an empty list. The call succeeds.
     */
    @Test
    void noMatchGivesEmptyList() {
        assertTrue(client.search("zzz").isEmpty());
    }

    /**
     * Case 6: "%" and "_" are literal text, not SQL wildcards. No name contains them.
     *
     * @param query the wildcard character
     */
    @ParameterizedTest
    @ValueSource(strings = {"%", "_"})
    void wildcardCharactersAreLiteral(String query) {
        assertTrue(client.search(query).isEmpty());
    }

    /**
     * A name with "%" matches the literal query "%" and only that name.
     */
    @Test
    void literalPercentMatchesOnlyThatName() {
        client.register(new Member("100% Coder", "coder@example.com"));
        assertEquals(List.of("100% Coder"), names(client.search("%")));
    }

    /**
     * Case 7: a blank query gives INVALID_ARGUMENT, which the client maps to IllegalArgumentException.
     *
     * @param query the blank query
     */
    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void blankQueryIsInvalidArgument(String query) {
        assertThrows(IllegalArgumentException.class, () -> client.search(query));
    }

    /**
     * Extract the names of the members, in order.
     *
     * @param members the members
     * @return the names
     */
    private static List<String> names(List<Member> members) {
        return members.stream().map(Member::getName).toList();
    }
}
