package org.viablespark.persistence

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.viablespark.persistence.dsl.Named
import org.viablespark.persistence.dsl.PrimaryKey
import org.viablespark.persistence.dsl.Ref
import org.viablespark.persistence.dsl.WithSql

class KotlinCompatibilityTest {

    private lateinit var db: EmbeddedDatabase
    private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setUp() {
        db = EmbeddedDatabaseBuilder()
            .addDefaultScripts()
            .setName("KotlinCompatibilityTest")
            .build()
        jdbc = JdbcTemplate(db)
    }

    @AfterEach
    fun tearDown() {
        db.shutdown()
    }

    @Test
    fun `maps Kotlin data class via PersistableRowMapper`() {
        val mapper = PersistableRowMapper.of(KotlinPurchaseOrderProjection::class.java)
        val sql = """
            select purchase_order.*, supplier.*
            from purchase_order
            left join supplier on supplier.id = purchase_order.supplier_id
            order by purchase_order.id
        """.trimIndent()
        val rowSet = jdbc.queryForRowSet(sql)
        var seenRows = 0
        while (rowSet.next()) {
            val entity = mapper.mapRow(rowSet, rowSet.row)!!
            assertNotNull(entity)
            assertEquals(rowSet.getString("requester"), entity.requester)
            assertEquals(rowSet.getLong("po_number_id"), entity.poNumberId)
            assertEquals(rowSet.getLong("primitive_id"), entity.primitiveExampleId)
            assertEquals(rowSet.getLong("long_id"), entity.longId)
            assertEquals("id", entity.refs.primaryKey().key)
            assertEquals(rowSet.getLong("id"), entity.refs.primaryKey().value)
            val note = entity.note!!
            assertNotNull(note)
            assertEquals("n_key", note.refs.primaryKey().key)
            assertEquals(rowSet.getLong("n_key"), note.refs.primaryKey().value)
            val supplierRef = entity.supplierRef
            assertNotNull(supplierRef)
            assertEquals(rowSet.getString("sup_name"), supplierRef!!.value)
            assertEquals(rowSet.getLong("supplier_id"), supplierRef.ref.value)
            seenRows++
        }
        assertTrue(seenRows > 0)
    }

    @Test
    fun `WithSql derives Kotlin clauses`() {
        val note = Note().apply { refs = Key.of("n_key", 1L) }
        val entity = KotlinPurchaseOrder(
            requester = "Kotlin Request",
            poNumberId = 55L,
            primitiveExampleId = 77L,
            longId = 99L,
            supplierId = 42,
        ).apply {
            this.note = note
        }
        entity.refs = Key.of("id", 5L)

        val updateClause = WithSql.getUpdateClause(entity)
        assertEquals(
            "SET long_id=?,n_key=?,po_number_id=?,primitive_id=?,requester=?,supplier_id=? WHERE id=?",
            updateClause.clause()
        )
        assertEquals(listOf(99L, 1L, 55L, 77L, "Kotlin Request", 42, 5L), updateClause.values().toList())

        val insertClause = WithSql.getInsertClause(entity.apply { refs = Key.None })
        assertEquals(
            "(long_id,n_key,po_number_id,primitive_id,requester,supplier_id) VALUES (?,?,?,?,?,?)",
            insertClause.clause()
        )
        assertEquals(listOf(99L, 1L, 55L, 77L, "Kotlin Request", 42), insertClause.values().toList())
    }

    @Test
    fun `BaseRepository saves and retrieves Kotlin entities`() {
        val repository = KotlinPurchaseOrderRepository(jdbc)
        val entity = KotlinPurchaseOrder(
            requester = "Kotlin Insert",
            poNumberId = 901L,
            primitiveExampleId = 902L,
            longId = 903L,
            supplierId = 1,
        ).apply {
            note = Note().apply { refs = Key.of("n_key", 1L) }
        }

        val savedKey = repository.save(entity)
        assertTrue(savedKey.isPresent)
        val key = savedKey.get()
        assertEquals("id", key.primaryKey().key)
        assertNotNull(key.primaryKey().value)

        val reloaded = repository.get(key, KotlinPurchaseOrder::class.java)
        assertTrue(reloaded.isPresent)
        val loadedEntity = reloaded.get()
        assertEquals("Kotlin Insert", loadedEntity.requester)
        assertEquals(901L, loadedEntity.poNumberId)
        assertEquals(902L, loadedEntity.primitiveExampleId)
        assertEquals(903L, loadedEntity.longId)
        assertEquals(1, loadedEntity.supplierId)
        assertNotNull(loadedEntity.note)
        assertEquals(1L, loadedEntity.note!!.refs.primaryKey().value)
    }
}

private class KotlinPurchaseOrderRepository(jdbc: JdbcTemplate) : BaseRepository<KotlinPurchaseOrder>(jdbc)

@PrimaryKey("id")
@Named("purchase_order")
data class KotlinPurchaseOrder(
    @get:Named("requester")
    var requester: String? = null,

    @get:Named("po_number_id")
    var poNumberId: Long? = null,

    @get:Named("primitive_id")
    var primitiveExampleId: Long? = null,

    @get:Named("long_id")
    var longId: Long? = null,

    @get:Ref
    var note: Note? = null,

    @get:Named("supplier_id")
    var supplierId: Int? = null,
) : Model()

@PrimaryKey("id")
@Named("purchase_order")
data class KotlinPurchaseOrderProjection(
    @get:Named("requester")
    var requester: String? = null,

    @get:Named("po_number_id")
    var poNumberId: Long? = null,

    @get:Named("primitive_id")
    var primitiveExampleId: Long? = null,

    @get:Named("long_id")
    var longId: Long? = null,

    @get:Ref
    var note: Note? = null,

    @get:Ref(value = "supplier_id", label = "sup_name")
    var supplierRef: RefValue? = null,
) : Model()
