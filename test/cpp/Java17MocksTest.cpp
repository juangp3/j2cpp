// A hand-written GoogleTest and gMock test of the mocks that j2c --mocks
// generates from test/src/.../Java17Mocks.java: the mocks are passed to the
// translated code, which calls them through the Java interfaces.
// vscode/scripts/mocks-gtest.js builds it (make tests USER_TEST_SRCS=...)
// against stubbed JDK classes, linking test/cpp/j2c_test_hooks.cpp.
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <se/arnetheduck/j2c/test/Java17Mocks.hpp>
#include <se/arnetheduck/j2c/test/Java17Mocks_Item.hpp>
#include <se/arnetheduck/j2c/test/MockJava17Mocks_Counter.hpp>
#include <se/arnetheduck/j2c/test/MockJava17Mocks_Fifteen.hpp>
#include <se/arnetheduck/j2c/test/MockJava17Mocks_Fixed.hpp>
#include <se/arnetheduck/j2c/test/MockJava17Mocks_Graded.hpp>
#include <se/arnetheduck/j2c/test/MockJava17Mocks_ItemSink.hpp>
#include <se/arnetheduck/j2c/test/MockJava17Mocks_ItemSource.hpp>
#include <se/arnetheduck/j2c/test/MockJava17Mocks_Measured.hpp>
#include <se/arnetheduck/j2c/test/MockJava17Mocks_Source.hpp>

using namespace se::arnetheduck::j2c::test;
using ::testing::_;
using ::testing::InSequence;
using ::testing::IsNull;
using ::testing::Return;

TEST(Java17Mocks, OverloadsAndKeywords)
{
	MockJava17Mocks_Counter counter;
	{
		InSequence order;
		EXPECT_CALL(counter, next()).Times(3).WillRepeatedly(Return(5));
		EXPECT_CALL(counter, add(15));
		EXPECT_CALL(counter, add(15, true));
		EXPECT_CALL(counter, delete_(3));
	}
	EXPECT_EQ(15, Java17Mocks::sumNext(&counter, 3));
}

TEST(Java17Mocks, ArraysAndVarargs)
{
	MockJava17Mocks_Counter counter;
	EXPECT_CALL(counter, total(IsNull(), 2.5, u'x', IsNull())).WillOnce(Return(99));
	Java17Mocks_Counter* asInterface = &counter;
	EXPECT_EQ(99, asInterface->total(nullptr, 2.5, u'x', nullptr));
}

TEST(Java17Mocks, DefaultMethodCallsTheMock)
{
	// Source.has(int) keeps its Java body, which calls the mocked get
	MockJava17Mocks_Source source;
	Java17Mocks_Item item;
	EXPECT_CALL(source, get(0)).WillOnce(Return(&item));
	EXPECT_CALL(source, get(1)).WillOnce(Return(nullptr));
	EXPECT_CALL(source, get(2)).WillOnce(Return(&item));
	EXPECT_EQ(2, Java17Mocks::present(&source, 3));
}

TEST(Java17Mocks, InheritedAndCovariant)
{
	MockJava17Mocks_ItemSource source;
	Java17Mocks_Item item;
	EXPECT_CALL(source, get(0)).WillOnce(Return(&item));
	EXPECT_CALL(source, name()).WillOnce(Return(nullptr));
	EXPECT_EQ(&item, Java17Mocks::first(&source));
	EXPECT_EQ(nullptr, static_cast<Java17Mocks_Named*>(&source)->name());
	EXPECT_CALL(source, convert(&item, IsNull())).WillOnce(Return(nullptr));
	EXPECT_EQ(nullptr, source.convert(&item, nullptr));
}

TEST(Java17Mocks, BridgeReachesTheMock)
{
	// Sink<T>.accept(Object) is the bridge to the mocked accept(Item)
	MockJava17Mocks_ItemSink sink;
	Java17Mocks_Item item;
	EXPECT_CALL(sink, accept(&item));
	Java17Mocks::feed(&sink, &item);
}

TEST(Java17Mocks, DefaultImplementationIsNotMocked)
{
	MockJava17Mocks_Fixed fixed;
	EXPECT_CALL(fixed, last()).WillOnce(Return(8));
	EXPECT_EQ(50, Java17Mocks::nextAndLast(&fixed));
}

TEST(Java17Mocks, RedeclaredDefaultForwards)
{
	// level() is Leveled's default; C++ redeclares it as pure in Graded
	MockJava17Mocks_Graded graded;
	EXPECT_CALL(graded, level(3)).WillOnce(Return(4));
	EXPECT_EQ(74, Java17Mocks::grade(&graded));
}

TEST(Java17Mocks, StrictMock)
{
	::testing::StrictMock<MockJava17Mocks_Counter> counter;
	EXPECT_CALL(counter, next()).WillOnce(Return(1));
	EXPECT_EQ(1, static_cast<Java17Mocks_Counter*>(&counter)->next());
}

TEST(Java17Mocks, FifteenParameters)
{
	MockJava17Mocks_Fifteen fifteen;
	EXPECT_CALL(fifteen, call(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, _)).WillOnce(Return(15));
	EXPECT_EQ(15, static_cast<Java17Mocks_Fifteen*>(&fifteen)->call(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14));
}

TEST(Java17Mocks, ReturnTypeOverridingUnrelatedErasures)
{
	MockJava17Mocks_Measured measured;
	EXPECT_CALL(measured, value()).Times(2).WillRepeatedly(Return(nullptr));
	EXPECT_EQ(nullptr, static_cast<Java17Mocks_Numbered*>(&measured)->value());
	EXPECT_EQ(nullptr, static_cast<Java17Mocks_Compared*>(&measured)->value());
}
